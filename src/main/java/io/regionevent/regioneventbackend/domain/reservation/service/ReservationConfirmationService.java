package io.regionevent.regioneventbackend.domain.reservation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyOperation;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecord;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecordStatus;
import io.regionevent.regioneventbackend.domain.idempotency.repository.IdempotencyRecordRepository;
import io.regionevent.regioneventbackend.domain.reservation.dto.ReservationConfirmationResponse;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ReservationConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationConfirmationService.class);
    private static final Duration IDEMPOTENCY_RESULT_RETENTION = Duration.ofHours(24);
    private static final int MAX_RESERVATION_IDENTIFIER_GENERATION_ATTEMPTS = 3;
    private static final String RESERVATION_NUMBER_UNIQUE_CONSTRAINT = "uk_reservation_reservation_no";
    private static final String QR_REFERENCE_UNIQUE_CONSTRAINT = "uk_reservation_qr_reference";

    private final AppUserRepository appUserRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ReservationConfirmationAuditService reservationConfirmationAuditService;
    private final ReservationNumberGenerator reservationNumberGenerator;
    private final ReservationIdempotencyLockWaitTimeoutConfigurer lockWaitTimeoutConfigurer;
    private final TransactionTemplate transactionTemplate;

    public ReservationConfirmationService(
        AppUserRepository appUserRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        IdempotencyRecordRepository idempotencyRecordRepository,
        ReservationConfirmationAuditService reservationConfirmationAuditService,
        ReservationNumberGenerator reservationNumberGenerator,
        ReservationIdempotencyLockWaitTimeoutConfigurer lockWaitTimeoutConfigurer,
        PlatformTransactionManager transactionManager
    ) {
        this.appUserRepository = appUserRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.reservationConfirmationAuditService = reservationConfirmationAuditService;
        this.reservationNumberGenerator = reservationNumberGenerator;
        this.lockWaitTimeoutConfigurer = lockWaitTimeoutConfigurer;
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
                if (isReservationIdentifierCollision(exception)
                    && attempt < MAX_RESERVATION_IDENTIFIER_GENERATION_ATTEMPTS) {
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
        AppUser actor = findActiveActor(actorUserId);
        String idempotencyKeyHash = hash(idempotencyKey);
        String requestHash = hash("holdId=" + holdId);

        IdempotencyRecord existingRecord = idempotencyRecordRepository
            .findByActor_UserIdAndOperationAndIdempotencyKeyHash(
                actorUserId,
                IdempotencyOperation.RESERVATION_CONFIRM,
                idempotencyKeyHash
            )
            .orElse(null);
        if (existingRecord != null) {
            return resolveExistingRecord(
                existingRecord,
                requestHash,
                requestId,
                actor,
                findOwnedCapacityHoldIfPresent(holdId, actor),
                holdId
            );
        }

        CapacityHold capacityHold = findOwnedCapacityHold(holdId, actor);
        Instant now = Instant.now();
        lockWaitTimeoutConfigurer.configureForCurrentTransaction();
        int insertedCount = insertProcessingIdempotencyRecord(
            actorUserId,
            idempotencyKeyHash,
            requestHash,
            now
        );
        IdempotencyRecord idempotencyRecord = idempotencyRecordRepository
            .findByActor_UserIdAndOperationAndIdempotencyKeyHash(
                actorUserId,
                IdempotencyOperation.RESERVATION_CONFIRM,
                idempotencyKeyHash
            )
            .orElseThrow(() -> new IllegalStateException("idempotency record must exist after reservation"));
        if (insertedCount == 0) {
            return resolveExistingRecord(idempotencyRecord, requestHash, requestId, actor, capacityHold, holdId);
        }

        Instant confirmedAt = capacityHoldRepository.findCurrentTimestamp();
        int consumedCount = capacityHoldRepository.consumeIfConfirmable(
            holdId,
            actorUserId,
            CapacityHoldStatus.ACTIVE,
            CapacityHoldStatus.CONSUMED,
            ContentStatus.PUBLISHED,
            ContentSessionStatus.SCHEDULED,
            confirmedAt
        );
        if (consumedCount == 0) {
            completeAsFailure(idempotencyRecord, requestId, actor, capacityHold, ErrorCode.RESERVATION_CONFIRM_CONFLICT);
        }
        capacityHold.consume(confirmedAt);

        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            reservationNumberGenerator.generate(),
            UUID.randomUUID().toString(),
            capacityHold.getRegion(),
            capacityHold,
            capacityHold.getContentSession(),
            actor,
            ReservationStatus.CONFIRMED,
            confirmedAt,
            null,
            null,
            null,
            null
        ));
        Instant completedAt = Instant.now();
        idempotencyRecord.completeAsSucceeded(
            reservation,
            completedAt,
            completedAt.plus(IDEMPOTENCY_RESULT_RETENTION)
        );
        reservationConfirmationAuditService.recordSuccess(
            requestId,
            actor,
            capacityHold,
            reservation,
            completedAt
        );
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
            reservationConfirmationAuditService.recordFailure(
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

    private int insertProcessingIdempotencyRecord(
        Long actorUserId,
        String idempotencyKeyHash,
        String requestHash,
        Instant createdAt
    ) {
        try {
            return idempotencyRecordRepository.insertProcessingIfAbsent(
                actorUserId,
                idempotencyKeyHash,
                requestHash,
                createdAt,
                createdAt.plus(IDEMPOTENCY_RESULT_RETENTION)
            );
        } catch (DataAccessException exception) {
            if (isLockWaitTimeout(exception)) {
                throw new IdempotencyLockWaitTimeoutException(exception);
            }
            throw exception;
        }
    }

    private static boolean isLockWaitTimeout(DataAccessException exception) {
        return hasMessage(exception, "lock wait timeout") || hasErrorCode(exception, 1205);
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

    private static boolean hasErrorCode(Throwable exception, int expectedErrorCode) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sqlException
                && sqlException.getErrorCode() == expectedErrorCode) {
                return true;
            }
        }
        return false;
    }

    private void completeAsFailure(
        IdempotencyRecord idempotencyRecord,
        String requestId,
        AppUser actor,
        CapacityHold capacityHold,
        ErrorCode errorCode
    ) {
        Instant completedAt = Instant.now();
        idempotencyRecord.completeAsFailed(
            errorCode,
            completedAt,
            completedAt.plus(IDEMPOTENCY_RESULT_RETENTION)
        );
        reservationConfirmationAuditService.recordFailure(
            requestId,
            actor,
            capacityHold,
            errorCode.code(),
            completedAt
        );
        throw new BusinessException(errorCode);
    }

    private AppUser findActiveActor(Long actorUserId) {
        AppUser actor = appUserRepository.findById(actorUserId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        if (actor.getStatus() != AppUserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return actor;
    }

    private CapacityHold findOwnedCapacityHold(Long holdId, AppUser actor) {
        CapacityHold capacityHold = capacityHoldRepository.findById(holdId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (capacityHold.getUser() == null || !actor.getUserId().equals(capacityHold.getUser().getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return capacityHold;
    }

    private CapacityHold findOwnedCapacityHoldIfPresent(Long holdId, AppUser actor) {
        return capacityHoldRepository.findById(holdId)
            .filter(capacityHold -> capacityHold.getUser() != null
                && actor.getUserId().equals(capacityHold.getUser().getUserId()))
            .orElse(null);
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

    private static class IdempotencyLockWaitTimeoutException extends RuntimeException {

        private IdempotencyLockWaitTimeoutException(DataAccessException cause) {
            super(cause);
        }
    }
}
