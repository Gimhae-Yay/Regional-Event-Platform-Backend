package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.service.RejectContentRevisionResult;

public record RejectContentRevisionResponse(
    String revisionId,
    String contentId,
    String revisionStatus,
    String contentStatus,
    String reviewReason,
    Instant reviewedAt
) {

    public static RejectContentRevisionResponse from(RejectContentRevisionResult result) {
        return new RejectContentRevisionResponse(
            result.revisionId().toString(),
            result.contentId().toString(),
            result.revisionStatus().name(),
            result.contentStatus().name(),
            result.reviewReason(),
            result.reviewedAt()
        );
    }
}
