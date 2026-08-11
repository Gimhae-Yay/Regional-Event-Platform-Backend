package io.regionevent.regioneventbackend.domain.payment.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;

public record GetMyRefundResponse(
    String refundId,
    String paymentId,
    String reservationId,
    long amount,
    String currency,
    RefundStatus status,
    Instant requestedAt,
    Instant completedAt
) {

    public static GetMyRefundResponse from(Refund refund) {
        Payment payment = requireNotNull(refund.getPayment(), "refund payment");
        Reservation reservation = requireNotNull(payment.getReservation(), "refund payment reservation");
        ReservationPriceSnapshot snapshot = requireNotNull(
            payment.getReservationPriceSnapshot(),
            "refund payment reservationPriceSnapshot"
        );
        return new GetMyRefundResponse(
            requireId(refund.getRefundId(), "refundId"),
            requireId(payment.getPaymentId(), "paymentId"),
            requireId(reservation.getReservationId(), "reservationId"),
            refund.getAmount(),
            requireNotNull(snapshot.getCurrency(), "currency"),
            requireNotNull(refund.getStatus(), "status"),
            requireNotNull(refund.getRequestedAt(), "requestedAt"),
            refund.getCompletedAt()
        );
    }

    private static String requireId(Long value, String fieldName) {
        return requireNotNull(value, fieldName).toString();
    }

    private static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException(fieldName + " must not be null");
        }
        return value;
    }
}
