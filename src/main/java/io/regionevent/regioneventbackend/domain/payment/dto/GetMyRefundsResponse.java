package io.regionevent.regioneventbackend.domain.payment.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;

public record GetMyRefundsResponse(List<RefundSummary> refunds) {

    public static GetMyRefundsResponse from(List<Refund> refunds) {
        return new GetMyRefundsResponse(refunds.stream()
            .map(RefundSummary::from)
            .toList());
    }

    public record RefundSummary(
        String refundId,
        String paymentId,
        String reservationId,
        long amount,
        String currency,
        RefundStatus status,
        Instant requestedAt,
        Instant completedAt
    ) {

        private static RefundSummary from(Refund refund) {
            Payment payment = requireNotNull(refund.getPayment(), "refund payment");
            Reservation reservation = requireNotNull(payment.getReservation(), "refund payment reservation");
            ReservationPriceSnapshot snapshot = requireNotNull(
                payment.getReservationPriceSnapshot(),
                "refund payment reservationPriceSnapshot"
            );
            return new RefundSummary(
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
}
