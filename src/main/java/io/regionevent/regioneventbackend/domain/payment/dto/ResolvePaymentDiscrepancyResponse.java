package io.regionevent.regioneventbackend.domain.payment.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.payment.service.ResolvePaymentDiscrepancyResult;

public record ResolvePaymentDiscrepancyResponse(
    String discrepancyId,
    String status,
    Instant resolvedAt
) {

    public static ResolvePaymentDiscrepancyResponse from(ResolvePaymentDiscrepancyResult result) {
        return new ResolvePaymentDiscrepancyResponse(
            String.valueOf(result.discrepancyId()),
            result.status(),
            result.resolvedAt()
        );
    }
}
