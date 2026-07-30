package io.regionevent.regioneventbackend.domain.reservation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyOperation;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecord;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecordStatus;
import io.regionevent.regioneventbackend.domain.idempotency.repository.IdempotencyRecordRepository;
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

    private static final Duration IDEMPOTENCY_RESULT_RETENTION = Duration.ofHours(24);

    private final AppUserRepository appUserRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ReservationConfirmationAuditService reservationConfirmationAuditService;
    private final ReservationNumberGenerator reservationNumberGenerator;

    public ReservationConfirmationService(
        AppUserRepository appUserRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        IdempotencyRecordRepository idempotencyRecordRepository,
        ReservationConfirmationAuditService reservationConfirmationAuditService,
        ReservationNumberGenerator reservationNumberGenerator
    ) {
        this.appUserRepository = appUserRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.reservationConfirmationAuditService = reservationConfirmationAuditService;
        this.reservationNumberGenerator = reservationNumberGenerator;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ReservationConfirmationResponse confirm(
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
            return resolveExistingRecord(existingRecord, requestHash);
        }
        CapacityHold capacityHold = findOwnedCapacityHold(holdId, actor);

        Instant now = Instant.now();
        int insertedCount = idempotencyRecordRepository.insertProcessingIfAbsent(
            actorUserId,
            idempotencyKeyHash,
            requestHash,
            now,
            now.plus(IDEMPOTENCY_RESULT_RETENTION)
        );
        IdempotencyRecord idempotencyRecord = idempotencyRecordRepository
            .findByActor_UserIdAndOperationAndIdempotencyKeyHash(
                actorUserId,
                IdempotencyOperation.RESERVATION_CONFIRM,
                idempotencyKeyHash
            )
            .orElseThrow(() -> new IllegalStateException("idempotency record must exist after reservation"));
        if (insertedCount == 0) {
            return resolveExistingRecord(idempotencyRecord, requestHash);
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
        String requestHash
    ) {
        if (!idempotencyRecord.getRequestHash().equals(requestHash)) {
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

    private static String hash(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(messageDigest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
