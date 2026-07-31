package io.regionevent.regioneventbackend.domain.idempotency.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyOperation;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecord;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecordStatus;
import io.regionevent.regioneventbackend.domain.idempotency.repository.IdempotencyRecordRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class IdempotencyRecordService {

    private static final Duration RESULT_RETENTION = Duration.ofHours(24);
    private static final List<IdempotencyRecordStatus> TERMINAL_STATUSES = List.of(
        IdempotencyRecordStatus.SUCCEEDED,
        IdempotencyRecordStatus.FAILED
    );

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final IdempotencyLockWaitTimeoutConfigurer lockWaitTimeoutConfigurer;

    public IdempotencyRecordService(
        IdempotencyRecordRepository idempotencyRecordRepository,
        IdempotencyLockWaitTimeoutConfigurer lockWaitTimeoutConfigurer
    ) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.lockWaitTimeoutConfigurer = lockWaitTimeoutConfigurer;
    }

    public IdempotencyRecord findReservationConfirmationRecord(Long actorUserId, String idempotencyKeyHash) {
        return idempotencyRecordRepository.findByActor_UserIdAndOperationAndIdempotencyKeyHash(
            actorUserId,
            IdempotencyOperation.RESERVATION_CONFIRM,
            idempotencyKeyHash
        ).orElse(null);
    }

    public IdempotencyClaim claimReservationConfirmation(
        Long actorUserId,
        String idempotencyKeyHash,
        String requestHash,
        Instant createdAt
    ) {
        try (IdempotencyLockWaitTimeoutConfigurer.LockWaitTimeoutScope ignored =
                 lockWaitTimeoutConfigurer.configureForCurrentTransaction()) {
            int insertedCount = idempotencyRecordRepository.insertProcessingIfAbsent(
                actorUserId,
                idempotencyKeyHash,
                requestHash,
                createdAt,
                createdAt.plus(RESULT_RETENTION)
            );
            IdempotencyRecord record = findReservationConfirmationRecord(actorUserId, idempotencyKeyHash);
            if (record == null) {
                throw new IllegalStateException("idempotency record must exist after claim");
            }
            return new IdempotencyClaim(record, insertedCount == 1);
        } catch (DataAccessException exception) {
            if (isLockWaitTimeout(exception)) {
                throw new IdempotencyLockWaitTimeoutException(exception);
            }
            throw exception;
        }
    }

    public void completeAsSucceeded(IdempotencyRecord idempotencyRecord, Reservation reservation, Instant completedAt) {
        idempotencyRecord.completeAsSucceeded(reservation, completedAt, completedAt.plus(RESULT_RETENTION));
    }

    public void completeAsFailed(IdempotencyRecord idempotencyRecord, ErrorCode errorCode, Instant completedAt) {
        idempotencyRecord.completeAsFailed(errorCode, completedAt, completedAt.plus(RESULT_RETENTION));
    }

    @Transactional
    public int deleteExpiredTerminalRecords() {
        return idempotencyRecordRepository.deleteExpiredTerminalRecords(TERMINAL_STATUSES);
    }

    private static boolean isLockWaitTimeout(DataAccessException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase().contains("lock wait timeout")) {
                return true;
            }
            if (cause instanceof java.sql.SQLException sqlException && sqlException.getErrorCode() == 1205) {
                return true;
            }
        }
        return false;
    }

    public record IdempotencyClaim(IdempotencyRecord record, boolean newlyClaimed) {
    }

    public static class IdempotencyLockWaitTimeoutException extends RuntimeException {

        public IdempotencyLockWaitTimeoutException(DataAccessException cause) {
            super(cause);
        }
    }
}
