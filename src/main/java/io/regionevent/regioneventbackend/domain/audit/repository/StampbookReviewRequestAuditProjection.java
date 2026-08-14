package io.regionevent.regioneventbackend.domain.audit.repository;

import java.time.Instant;

public record StampbookReviewRequestAuditProjection(
    Instant requestedAt,
    String requestReason
) {
}
