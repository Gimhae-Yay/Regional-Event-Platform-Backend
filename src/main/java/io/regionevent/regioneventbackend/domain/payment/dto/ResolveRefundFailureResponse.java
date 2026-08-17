package io.regionevent.regioneventbackend.domain.payment.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.payment.service.ResolveRefundFailureResult;

public record ResolveRefundFailureResponse(
    String refundId,
    String status,
    Instant resolvedAt
) {

    public static ResolveRefundFailureResponse from(ResolveRefundFailureResult result) {
        return new ResolveRefundFailureResponse(
            String.valueOf(result.refundId()),
            result.status(),
            result.resolvedAt()
        );
    }
}
