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
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"capacityHold", "reservationPriceSnapshot", "reservationPriceSnapshot.coupon", "reservation"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT payment FROM Payment payment WHERE payment.paymentId = :paymentId")
    Optional<Payment> findByPaymentIdForUpdate(@Param("paymentId") Long paymentId);

    @EntityGraph(attributePaths = {"capacityHold", "reservationPriceSnapshot", "reservationPriceSnapshot.coupon", "reservation"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT payment FROM Payment payment WHERE payment.reservation.reservationId = :reservationId")
    Optional<Payment> findByReservationIdForUpdate(@Param("reservationId") Long reservationId);

    @EntityGraph(attributePaths = {"capacityHold", "reservationPriceSnapshot", "reservationPriceSnapshot.coupon", "reservation"})
    Optional<Payment> findByReservationReservationId(Long reservationId);

    @EntityGraph(attributePaths = {"capacityHold", "capacityHold.user", "reservationPriceSnapshot", "reservation"})
    Optional<Payment> findByPaymentId(Long paymentId);

    @EntityGraph(attributePaths = {"capacityHold", "reservationPriceSnapshot", "reservationPriceSnapshot.coupon", "reservation"})
    Optional<Payment> findByOrderId(String orderId);

    @EntityGraph(attributePaths = {"capacityHold", "reservationPriceSnapshot", "reservationPriceSnapshot.coupon", "reservation"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT payment
        FROM Payment payment
        WHERE payment.orderId = :orderId
        """)
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT payment
        FROM Payment payment
        WHERE payment.orderId = :orderId
        """)
    Optional<Payment> findWebhookTargetByOrderIdForUpdate(@Param("orderId") String orderId);

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

    boolean existsByCapacityHoldUserUserIdAndStatus(Long userId, PaymentStatus status);

    boolean existsByCapacityHoldUserUserIdAndStatusAndReservationStatus(
        Long userId,
        PaymentStatus paymentStatus,
        ReservationStatus reservationStatus
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
