package io.regionevent.regioneventbackend.domain.idempotency.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecord;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyOperation;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecordStatus;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByActor_UserIdAndOperationAndIdempotencyKeyHash(
        Long userId,
        IdempotencyOperation operation,
        String idempotencyKeyHash
    );

    @Modifying
    @Query(value = """
        INSERT IGNORE INTO idempotency_record (
            actor_user_id,
            operation,
            idempotency_key_hash,
            request_hash,
            status,
            created_at,
            expires_at
        ) VALUES (
            :actorUserId,
            'RESERVATION_CONFIRM',
            :idempotencyKeyHash,
            :requestHash,
            'PROCESSING',
            :createdAt,
            :expiresAt
        )
        ON DUPLICATE KEY UPDATE
            idempotency_record_id = idempotency_record_id
        """, nativeQuery = true)
    int insertProcessingIfAbsent(
        @Param("actorUserId") Long actorUserId,
        @Param("idempotencyKeyHash") String idempotencyKeyHash,
        @Param("requestHash") String requestHash,
        @Param("createdAt") Instant createdAt,
        @Param("expiresAt") Instant expiresAt
    );

    @Modifying
    @Query("""
        DELETE FROM IdempotencyRecord idempotencyRecord
        WHERE idempotencyRecord.status IN :terminalStatuses
          AND idempotencyRecord.expiresAt < CURRENT_TIMESTAMP
        """)
    int deleteExpiredTerminalRecords(
        @Param("terminalStatuses") List<IdempotencyRecordStatus> terminalStatuses
    );
}
