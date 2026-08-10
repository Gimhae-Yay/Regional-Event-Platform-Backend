package io.regionevent.regioneventbackend.domain.payment.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"capacityHold", "reservationPriceSnapshot", "reservationPriceSnapshot.coupon", "reservation"})
    Optional<Payment> findByOrderId(String orderId);

    @EntityGraph(attributePaths = {"capacityHold", "reservationPriceSnapshot", "reservationPriceSnapshot.coupon", "reservation"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT payment
        FROM Payment payment
        WHERE payment.capacityHold.holdId = :holdId
          AND payment.status = :status
        """)
    Optional<Payment> findByHoldIdAndStatusForUpdate(
        @Param("holdId") Long holdId,
        @Param("status") PaymentStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT payment
        FROM Payment payment
        WHERE payment.portonePaymentId = :portonePaymentId
        """)
    Optional<Payment> findByPortonePaymentIdForUpdate(
        @Param("portonePaymentId") String portonePaymentId
    );
}
