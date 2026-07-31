package io.regionevent.regioneventbackend.domain.reservation.usecase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.usecase.ReservationConfirmationAuditUseCase;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecord;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecordStatus;
import io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyRecordService;
import io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyRecordService.IdempotencyClaim;
import io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyRecordService.IdempotencyLockWaitTimeoutException;
import io.regionevent.regioneventbackend.domain.reservation.dto.ReservationConfirmationResponse;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ReservationConfirmationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReservationConfirmationUseCase.class);
    private static final int MAX_RESERVATION_IDENTIFIER_GENERATION_ATTEMPTS = 3;
    private static final String RESERVATION_NUMBER_UNIQUE_CONSTRAINT = "uk_reservation_reservation_no";
    private static final String QR_REFERENCE_UNIQUE_CONSTRAINT = "uk_reservation_qr_reference";

    private final AppUserService appUserService;
    private final CapacityHoldService capacityHoldService;
    private final ReservationService reservationService;
    private final IdempotencyRecordService idempotencyRecordService;
    private final ReservationConfirmationAuditUseCase reservationConfirmationAuditUseCase;
    private final TransactionTemplate transactionTemplate;

    public ReservationConfirmationUseCase(
        AppUserService appUserService,
        CapacityHoldService capacityHoldService,
        ReservationService reservationService,
        IdempotencyRecordService idempotencyRecordService,
        ReservationConfirmationAuditUseCase reservationConfirmationAuditUseCase,
        PlatformTransactionManager transactionManager
    ) {
        this.appUserService = appUserService;
        this.capacityHoldService = capacityHoldService;
        this.reservationService = reservationService;
        this.idempotencyRecordService = idempotencyRecordService;
        this.reservationConfirmationAuditUseCase = reservationConfirmationAuditUseCase;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ReservationConfirmationResponse confirm(
        Long actorUserId,
        Long holdId,
        String idempotencyKey,
        String requestId
    ) {
        for (int attempt = 1; attempt <= MAX_RESERVATION_IDENTIFIER_GENERATION_ATTEMPTS; attempt++) {
            try {
                ReservationConfirmationAttempt confirmationAttempt = transactionTemplate.execute(status ->
                    confirmInTransaction(actorUserId, holdId, idempotencyKey, requestId)
                );
                return confirmationAttempt.resolve();
            } catch (IdempotencyLockWaitTimeoutException exception) {
                log.warn(
                    "Reservation idempotency request is in progress. requestId={}, actorUserId={}, holdId={}, reasonCode={}",
                    requestId,
                    actorUserId,
                    holdId,
                    ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS.code()
                );
                throw new BusinessException(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS, exception);
            } catch (DataAccessException exception) {
                if (isReservationIdentifierCollision(exception) && attempt < MAX_RESERVATION_IDENTIFIER_GENERATION_ATTEMPTS) {
                    log.warn(
                        "Reservation identifier collision. requestId={}, actorUserId={}, holdId={}, attempt={}",
                        requestId,
                        actorUserId,
                        holdId,
                        attempt
                    );
                    continue;
                }
                throw exception;
            }
        }
        throw new IllegalStateException("reservation identifier generation attempts must be exhausted by a database error");
    }

    private ReservationConfirmationAttempt confirmInTransaction(
        Long actorUserId,
        Long holdId,
        String idempotencyKey,
        String requestId
    ) {
        try {
            return ReservationConfirmationAttempt.succeeded(confirmOnce(actorUserId, holdId, idempotencyKey, requestId));
        } catch (BusinessException exception) {
            return ReservationConfirmationAttempt.failed(exception);
        }
    }

    private ReservationConfirmationResponse confirmOnce(
        Long actorUserId,
        Long holdId,
        String idempotencyKey,
        String requestId
    ) {
        AppUser actor = appUserService.findActiveUser(actorUserId);
        String idempotencyKeyHash = hash(idempotencyKey);
        String requestHash = hash("holdId=" + holdId);
        IdempotencyRecord existingRecord = idempotencyRecordService
            .findReservationConfirmationRecord(actorUserId, idempotencyKeyHash);
        if (existingRecord != null) {
            return resolveExistingRecord(
                existingRecord,
                requestHash,
                requestId,
                actor,
                capacityHoldService.findOwnedHoldIfPresent(holdId, actor),
                holdId
            );
        }

        CapacityHold capacityHold = capacityHoldService.findOwnedHold(holdId, actor);
        IdempotencyClaim claim = idempotencyRecordService.claimReservationConfirmation(
            actorUserId,
            idempotencyKeyHash,
            requestHash,
            Instant.now()
        );
        if (!claim.newlyClaimed()) {
            return resolveExistingRecord(claim.record(), requestHash, requestId, actor, capacityHold, holdId);
        }

        Instant confirmedAt = capacityHoldService.consumeIfConfirmable(capacityHold, actor);
        if (confirmedAt == null) {
            completeAsFailure(claim.record(), requestId, actor, capacityHold, ErrorCode.RESERVATION_CONFIRM_CONFLICT);
        }
        Reservation reservation = reservationService.createConfirmed(capacityHold, actor, confirmedAt);
        Instant completedAt = Instant.now();
        idempotencyRecordService.completeAsSucceeded(claim.record(), reservation, completedAt);
        reservationConfirmationAuditUseCase.recordSuccess(requestId, actor, capacityHold, reservation, completedAt);
        return ReservationConfirmationResponse.from(reservation);
    }

    private ReservationConfirmationResponse resolveExistingRecord(
        IdempotencyRecord idempotencyRecord,
        String requestHash,
        String requestId,
        AppUser actor,
        CapacityHold capacityHold,
        Long holdId
    ) {
        if (!idempotencyRecord.getRequestHash().equals(requestHash)) {
            recordIdempotencyKeyConflict(requestId, actor, capacityHold, holdId);
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        if (idempotencyRecord.getStatus() == IdempotencyRecordStatus.SUCCEEDED) {
            return ReservationConfirmationResponse.from(idempotencyRecord.getResultReservation());
        }
        if (idempotencyRecord.getStatus() == IdempotencyRecordStatus.FAILED) {
            throw new BusinessException(ErrorCode.fromCode(idempotencyRecord.getResultCode()));
        }
        throw new BusinessException(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
    }

    private void recordIdempotencyKeyConflict(
        String requestId,
        AppUser actor,
        CapacityHold capacityHold,
        Long holdId
    ) {
        log.warn(
            "Reservation idempotency key conflict. requestId={}, actorUserId={}, holdId={}, reasonCode={}",
            requestId,
            actor.getUserId(),
            holdId,
            ErrorCode.IDEMPOTENCY_KEY_CONFLICT.code()
        );
        try {
            reservationConfirmationAuditUseCase.recordFailure(
                requestId,
                actor,
                capacityHold,
                ErrorCode.IDEMPOTENCY_KEY_CONFLICT.code(),
                Instant.now()
            );
        } catch (RuntimeException exception) {
            log.error(
                "Failed to record reservation idempotency key conflict audit event. requestId={}, reasonCode={}",
                requestId,
                ErrorCode.IDEMPOTENCY_KEY_CONFLICT.code(),
                exception
            );
        }
    }

    private void completeAsFailure(
        IdempotencyRecord idempotencyRecord,
        String requestId,
        AppUser actor,
        CapacityHold capacityHold,
        ErrorCode errorCode
    ) {
        Instant completedAt = Instant.now();
        idempotencyRecordService.completeAsFailed(idempotencyRecord, errorCode, completedAt);
        reservationConfirmationAuditUseCase.recordFailure(requestId, actor, capacityHold, errorCode.code(), completedAt);
        throw new BusinessException(errorCode);
    }

    private static boolean isReservationIdentifierCollision(DataAccessException exception) {
        return hasMessage(exception, RESERVATION_NUMBER_UNIQUE_CONSTRAINT)
            || hasMessage(exception, QR_REFERENCE_UNIQUE_CONSTRAINT);
    }

    private static boolean hasMessage(Throwable exception, String expectedMessage) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase().contains(expectedMessage)) {
                return true;
            }
        }
        return false;
    }

    private static String hash(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(messageDigest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private record ReservationConfirmationAttempt(
        ReservationConfirmationResponse response,
        BusinessException exception
    ) {

        private static ReservationConfirmationAttempt succeeded(ReservationConfirmationResponse response) {
            return new ReservationConfirmationAttempt(response, null);
        }

        private static ReservationConfirmationAttempt failed(BusinessException exception) {
            return new ReservationConfirmationAttempt(null, exception);
        }

        private ReservationConfirmationResponse resolve() {
            if (exception != null) {
                throw exception;
            }
            return response;
        }
    }
}
