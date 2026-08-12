package io.regionevent.regioneventbackend.domain.payment.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;

public record RetryRefundResponse(
    String refundId,
    int attemptNo,
    String status,
    Instant attemptedAt
) {

    public static RetryRefundResponse from(
        Refund refund,
        RefundAttempt attempt
    ) {
        return new RetryRefundResponse(
            String.valueOf(refund.getRefundId()),
            attempt.getAttemptNo(),
            refund.getStatus().name(),
            attempt.getAttemptedAt()
        );
    }
}
