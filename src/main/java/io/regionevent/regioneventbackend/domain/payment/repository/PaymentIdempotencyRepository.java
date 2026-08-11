package io.regionevent.regioneventbackend.domain.payment.repository;

import java.util.Collection;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.payment.entity.PaymentIdempotency;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentIdempotencyOperation;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentIdempotencyStatus;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT idempotency
        FROM PaymentIdempotency idempotency
        WHERE idempotency.payment.paymentId = :paymentId
        """)
    Optional<PaymentIdempotency> findByPaymentPaymentIdForUpdate(@Param("paymentId") Long paymentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        DELETE FROM PaymentIdempotency idempotency
        WHERE idempotency.status IN :terminalStatuses
            AND idempotency.expiresAt < CURRENT_TIMESTAMP
        """)
    int deleteExpiredTerminalRecords(
        @Param("terminalStatuses") Collection<PaymentIdempotencyStatus> terminalStatuses
    );
}
