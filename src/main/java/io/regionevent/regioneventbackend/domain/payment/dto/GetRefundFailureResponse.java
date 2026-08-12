package io.regionevent.regioneventbackend.domain.payment.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.payment.service.RefundFailureDetailInfo;

public record GetRefundFailureResponse(
    RefundResponse refund,
    PaymentResponse payment,
    List<RefundAttemptResponse> attempts
) {

    public GetRefundFailureResponse {
        attempts = List.copyOf(attempts);
    }

    public static GetRefundFailureResponse from(RefundFailureDetailInfo detail) {
        return new GetRefundFailureResponse(
            RefundResponse.from(detail.refund()),
            PaymentResponse.from(detail.payment()),
            detail.attempts().stream().map(RefundAttemptResponse::from).toList()
        );
    }

    public record RefundResponse(
        String refundId,
        String paymentId,
        String reservationId,
        long amount,
        String currency,
        String status,
        Instant requestedAt,
        Instant completedAt
    ) {

        private static RefundResponse from(RefundFailureDetailInfo.RefundInfo refund) {
            return new RefundResponse(
                refund.refundId().toString(),
                refund.paymentId().toString(),
                refund.reservationId().toString(),
                refund.amount(),
                refund.currency(),
                refund.status().name(),
                refund.requestedAt(),
                refund.completedAt()
            );
        }
    }

    public record PaymentResponse(
        String paymentId,
        String orderId,
        String portonePaymentId,
        long finalAmount,
        String currency
    ) {

        private static PaymentResponse from(RefundFailureDetailInfo.PaymentInfo payment) {
            return new PaymentResponse(
                payment.paymentId().toString(),
                payment.orderId(),
                payment.portonePaymentId(),
                payment.finalAmount(),
                payment.currency()
            );
        }
    }

    public record RefundAttemptResponse(
        String refundAttemptId,
        int attemptNo,
        String initiatorKind,
        String portoneCancellationId,
        String outcomeKind,
        String failureReasonCode,
        String externalStatus,
        Instant attemptedAt
    ) {

        private static RefundAttemptResponse from(RefundFailureDetailInfo.AttemptInfo attempt) {
            return new RefundAttemptResponse(
                attempt.refundAttemptId().toString(),
                attempt.attemptNo(),
                attempt.initiatorKind().name(),
                attempt.portoneCancellationId(),
                attempt.outcomeKind().name(),
                attempt.failureReasonCode() == null ? null : attempt.failureReasonCode().name(),
                attempt.externalStatus(),
                attempt.attemptedAt()
            );
        }
    }
}
