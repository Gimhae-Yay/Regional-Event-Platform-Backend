package io.regionevent.regioneventbackend.domain.payment.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.payment.service.RefundFailureListInfo;

public record GetRefundFailuresResponse(List<RefundFailureResponse> refunds) {

    public GetRefundFailuresResponse {
        refunds = List.copyOf(refunds);
    }

    public static GetRefundFailuresResponse from(List<RefundFailureListInfo> refunds) {
        return new GetRefundFailuresResponse(refunds.stream()
            .map(RefundFailureResponse::from)
            .toList());
    }

    public record RefundFailureResponse(
        String refundId,
        String paymentId,
        String reservationId,
        long amount,
        String currency,
        String status,
        int attemptCount,
        Instant requestedAt,
        Instant updatedAt
    ) {

        private static RefundFailureResponse from(RefundFailureListInfo refund) {
            return new RefundFailureResponse(
                refund.refundId().toString(),
                refund.paymentId().toString(),
                refund.reservationId() == null ? null : refund.reservationId().toString(),
                refund.amount(),
                refund.currency(),
                refund.status().name(),
                refund.attemptCount(),
                refund.requestedAt(),
                refund.updatedAt()
            );
        }
    }
}
