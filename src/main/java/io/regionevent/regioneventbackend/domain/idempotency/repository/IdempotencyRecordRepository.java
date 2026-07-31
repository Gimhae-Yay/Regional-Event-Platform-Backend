package io.regionevent.regioneventbackend.domain.idempotency.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyOperation;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecord;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecordStatus;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    @Query("""
        SELECT record
        FROM IdempotencyRecord record
        WHERE record.actor.userId = :actorUserId
            AND record.operation = :operation
            AND record.idempotencyKeyHash = :idempotencyKeyHash
        """)
    Optional<IdempotencyRecord> findByActorUserIdAndOperationAndIdempotencyKeyHash(
        @Param("actorUserId") Long actorUserId,
        @Param("operation") IdempotencyOperation operation,
        @Param("idempotencyKeyHash") String idempotencyKeyHash
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        DELETE FROM IdempotencyRecord record
        WHERE record.status IN :terminalStatuses
            AND record.expiresAt < :now
        """)
    int deleteExpiredByStatusInAndExpiresAtBefore(
        @Param("terminalStatuses") Collection<IdempotencyRecordStatus> terminalStatuses,
        @Param("now") Instant now
    );
}
