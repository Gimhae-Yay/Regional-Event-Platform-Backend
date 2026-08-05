package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;

public record RejectSessionRevisionResult(
    Long revisionId,
    SessionRevisionStatus revisionStatus,
    Long contentId,
    Long targetSessionId,
    String rejectReason,
    Instant reviewedAt
) {
}
