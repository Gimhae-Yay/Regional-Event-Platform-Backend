package io.regionevent.regioneventbackend.domain.payment.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;

public record GetMyPaymentResponse(
    String paymentId,
    String holdId,
    String orderId,
    PaymentStatus status,
    AmountResponse amount,
    String reservationId,
    Instant createdAt,
    Instant finalizedAt
) {

    public static GetMyPaymentResponse from(Payment payment) {
        ReservationPriceSnapshot snapshot = payment.getReservationPriceSnapshot();
        return new GetMyPaymentResponse(
            payment.getPaymentId().toString(),
            payment.getCapacityHold().getHoldId().toString(),
            payment.getOrderId(),
            payment.getStatus(),
            new AmountResponse(
                snapshot.getBaseAmount(),
                snapshot.getDiscountAmount(),
                snapshot.getFinalAmount(),
                snapshot.getCurrency()
            ),
            toReservationId(payment),
            payment.getCreatedAt(),
            payment.getFinalizedAt()
        );
    }

    private static String toReservationId(Payment payment) {
        if (payment.getStatus() != PaymentStatus.APPROVED) {
            return null;
        }

        Reservation reservation = payment.getReservation();
        if (reservation == null || reservation.getReservationId() == null) {
            throw new IllegalStateException("approved payment must have reservation");
        }
        return reservation.getReservationId().toString();
    }

    public record AmountResponse(
        long baseAmount,
        long discountAmount,
        long finalAmount,
        String currency
    ) {
    }
}
