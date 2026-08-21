package io.regionevent.regioneventbackend.domain.payment.service;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;

public record RefundFailureListInfo(
    Long refundId,
    Long paymentId,
    Long reservationId,
    long amount,
    String currency,
    RefundStatus status,
    int attemptCount,
    Instant requestedAt,
    Instant updatedAt
) {

    public static RefundFailureListInfo from(
        Refund refund,
        List<RefundAttempt> attempts
    ) {
        Payment payment = requireNotNull(refund.getPayment(), "refund payment");
        Reservation reservation = payment.getReservation();
        Long reservationId = reservation == null
            ? null
            : requireNotNull(reservation.getReservationId(), "reservationId");
        ReservationPriceSnapshot snapshot = requireNotNull(
            payment.getReservationPriceSnapshot(),
            "refund payment reservationPriceSnapshot"
        );
        Instant updatedAt = latestUpdatedAt(refund, attempts);
        return new RefundFailureListInfo(
            requireNotNull(refund.getRefundId(), "refundId"),
            requireNotNull(payment.getPaymentId(), "paymentId"),
            reservationId,
            refund.getAmount(),
            requireNotNull(snapshot.getCurrency(), "currency"),
            requireNotNull(refund.getStatus(), "status"),
            attempts.size(),
            requireNotNull(refund.getRequestedAt(), "requestedAt"),
            updatedAt
        );
    }

    private static Instant latestUpdatedAt(
        Refund refund,
        List<RefundAttempt> attempts
    ) {
        Instant latestUpdatedAt = refund.getRequestedAt();
        if (refund.getCompletedAt() != null && refund.getCompletedAt().isAfter(latestUpdatedAt)) {
            latestUpdatedAt = refund.getCompletedAt();
        }
        if (refund.getResolvedAt() != null && refund.getResolvedAt().isAfter(latestUpdatedAt)) {
            latestUpdatedAt = refund.getResolvedAt();
        }
        for (RefundAttempt attempt : attempts) {
            if (attempt.getAttemptedAt().isAfter(latestUpdatedAt)) {
                latestUpdatedAt = attempt.getAttemptedAt();
            }
        }
        return latestUpdatedAt;
    }

    private static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException(fieldName + " must not be null");
        }
        return value;
    }
}
