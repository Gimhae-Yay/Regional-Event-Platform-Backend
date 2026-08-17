package io.regionevent.regioneventbackend.domain.payment.service;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptInitiatorKind;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptOutcomeKind;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundFailureReasonCode;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;

public record RefundFailureDetailInfo(
    RefundInfo refund,
    PaymentInfo payment,
    List<AttemptInfo> attempts
) {

    public RefundFailureDetailInfo {
        attempts = List.copyOf(attempts);
    }

    public static RefundFailureDetailInfo from(Refund refund, List<RefundAttempt> attempts) {
        Payment payment = requireNotNull(refund.getPayment(), "refund payment");
        Reservation reservation = requireNotNull(payment.getReservation(), "refund payment reservation");
        ReservationPriceSnapshot snapshot = requireNotNull(
            payment.getReservationPriceSnapshot(),
            "refund payment reservationPriceSnapshot"
        );
        return new RefundFailureDetailInfo(
            new RefundInfo(
                requireNotNull(refund.getRefundId(), "refundId"),
                requireNotNull(payment.getPaymentId(), "paymentId"),
                requireNotNull(reservation.getReservationId(), "reservationId"),
                refund.getAmount(),
                requireNotNull(snapshot.getCurrency(), "currency"),
                requireNotNull(refund.getStatus(), "status"),
                requireNotNull(refund.getRequestedAt(), "requestedAt"),
                refund.getCompletedAt()
            ),
            new PaymentInfo(
                requireNotNull(payment.getPaymentId(), "paymentId"),
                requireNotNull(payment.getOrderId(), "orderId"),
                payment.getPortonePaymentId(),
                snapshot.getFinalAmount(),
                requireNotNull(snapshot.getCurrency(), "currency")
            ),
            attempts.stream().map(AttemptInfo::from).toList()
        );
    }

    public record RefundInfo(
        Long refundId,
        Long paymentId,
        Long reservationId,
        long amount,
        String currency,
        RefundStatus status,
        Instant requestedAt,
        Instant completedAt
    ) {
    }

    public record PaymentInfo(
        Long paymentId,
        String orderId,
        String portonePaymentId,
        long finalAmount,
        String currency
    ) {
    }

    public record AttemptInfo(
        Long refundAttemptId,
        int attemptNo,
        RefundAttemptInitiatorKind initiatorKind,
        String portoneCancellationId,
        RefundAttemptOutcomeKind outcomeKind,
        RefundFailureReasonCode failureReasonCode,
        String externalStatus,
        Instant attemptedAt
    ) {

        private static AttemptInfo from(RefundAttempt attempt) {
            return new AttemptInfo(
                requireNotNull(attempt.getRefundAttemptId(), "refundAttemptId"),
                attempt.getAttemptNo(),
                requireNotNull(attempt.getInitiatorKind(), "initiatorKind"),
                attempt.getPortoneCancellationId(),
                requireNotNull(attempt.getOutcomeKind(), "outcomeKind"),
                attempt.getFailureReasonCode(),
                attempt.getExternalStatus(),
                requireNotNull(attempt.getAttemptedAt(), "attemptedAt")
            );
        }
    }

    private static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException(fieldName + " must not be null");
        }
        return value;
    }
}
