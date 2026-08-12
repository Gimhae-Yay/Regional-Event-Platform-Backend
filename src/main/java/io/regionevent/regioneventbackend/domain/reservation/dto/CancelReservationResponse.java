package io.regionevent.regioneventbackend.domain.reservation.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.payment.dto.CreateRefundResponse;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;

public record CancelReservationResponse(
    String reservationId,
    String reservationStatus,
    RefundSummary refund,
    String sessionId,
    String status,
    String cancellationReason,
    Instant cancelledAt,
    Instant capacityReleasedAt
) {

    public static CancelReservationResponse from(Reservation reservation) {
        return from(reservation, null);
    }

    public static CancelReservationResponse from(
        Reservation reservation,
        CreateRefundResponse refund
    ) {
        return new CancelReservationResponse(
            reservation.getReservationId().toString(),
            reservation.getStatus().name(),
            RefundSummary.from(refund),
            reservation.getContentSession().getSessionId().toString(),
            reservation.getStatus().name(),
            reservation.getCancellationReason(),
            reservation.getCancelledAt(),
            reservation.getCapacityReleasedAt()
        );
    }

    public record RefundSummary(
        String refundId,
        String paymentId,
        long amount,
        String currency,
        String status,
        Instant requestedAt
    ) {

        private static RefundSummary from(CreateRefundResponse refund) {
            if (refund == null) {
                return null;
            }
            return new RefundSummary(
                refund.refundId(),
                refund.paymentId(),
                refund.amount(),
                refund.currency(),
                refund.status(),
                refund.requestedAt()
            );
        }
    }
}
