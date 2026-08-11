package io.regionevent.regioneventbackend.domain.payment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    @EntityGraph(attributePaths = {"payment", "payment.reservationPriceSnapshot", "payment.reservation"})
    @Query("""
        SELECT refund
        FROM Refund refund
        WHERE refund.payment.capacityHold.user.userId = :userId
        ORDER BY refund.requestedAt DESC, refund.refundId DESC
        """)
    List<Refund> findAllByPaymentOwnerUserIdOrderByRequestedAtDescRefundIdDesc(
        @Param("userId") Long userId
    );

    @EntityGraph(attributePaths = {"payment", "payment.reservationPriceSnapshot", "payment.reservation"})
    @Query("""
        SELECT refund
        FROM Refund refund
        WHERE refund.refundId = :refundId
          AND refund.payment.capacityHold.user.userId = :userId
        """)
    Optional<Refund> findByRefundIdAndPaymentOwnerUserId(
        @Param("refundId") Long refundId,
        @Param("userId") Long userId
    );

    boolean existsByPaymentCapacityHoldUserUserIdAndStatusIn(
        Long userId,
        Collection<RefundStatus> statuses
    );

    @EntityGraph(attributePaths = {"payment", "payment.reservationPriceSnapshot"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT refund
        FROM Refund refund
        WHERE refund.payment.paymentId = :paymentId
        """)
    Optional<Refund> findByPaymentIdForUpdate(@Param("paymentId") Long paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT refund
        FROM Refund refund
        WHERE refund.refundId = :refundId
        """)
    Optional<Refund> findByRefundIdForUpdate(@Param("refundId") Long refundId);
}
