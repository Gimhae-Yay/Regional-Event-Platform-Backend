package io.regionevent.regioneventbackend.domain.payment.service;

import java.time.Instant;

public record ResolvePaymentDiscrepancyResult(
    Long discrepancyId,
    String status,
    Instant resolvedAt
) {
}
