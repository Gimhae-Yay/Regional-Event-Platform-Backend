package io.regionevent.regioneventbackend.domain.payment.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.payment.entity.PaymentIdempotency;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentIdempotencyOperation;

public interface PaymentIdempotencyRepository extends JpaRepository<PaymentIdempotency, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT idempotency
        FROM PaymentIdempotency idempotency
        WHERE idempotency.actorUserId = :actorUserId
          AND idempotency.operation = :operation
          AND idempotency.idempotencyKeyHash = :idempotencyKeyHash
        """)
    Optional<PaymentIdempotency> findByActorUserIdAndOperationAndIdempotencyKeyHashForUpdate(
        @Param("actorUserId") long actorUserId,
        @Param("operation") PaymentIdempotencyOperation operation,
        @Param("idempotencyKeyHash") String idempotencyKeyHash
    );
}
