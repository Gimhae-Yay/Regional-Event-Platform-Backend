package io.regionevent.regioneventbackend.domain.payment.service;

import java.time.Instant;

public record ResolveRefundFailureResult(
    Long refundId,
    String status,
    Instant resolvedAt
) {
}
