package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.service.RejectSessionRevisionResult;

public record RejectSessionRevisionResponse(
    String revisionId,
    String revisionStatus,
    String contentId,
    String targetSessionId,
    String rejectReason,
    Instant reviewedAt
) {

    public static RejectSessionRevisionResponse from(RejectSessionRevisionResult result) {
        return new RejectSessionRevisionResponse(
            result.revisionId().toString(),
            result.revisionStatus().name(),
            result.contentId().toString(),
            result.targetSessionId().toString(),
            result.rejectReason(),
            result.reviewedAt()
        );
    }
}
