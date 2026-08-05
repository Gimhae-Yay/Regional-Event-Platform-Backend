package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.service.ApproveSessionRevisionResult;

public record ApproveSessionRevisionResponse(
    String revisionId,
    String revisionStatus,
    String contentId,
    String targetSessionId,
    int sessionVersion,
    Instant reviewedAt
) {

    public static ApproveSessionRevisionResponse from(ApproveSessionRevisionResult result) {
        return new ApproveSessionRevisionResponse(
            result.revisionId().toString(),
            result.revisionStatus().name(),
            result.contentId().toString(),
            result.targetSessionId().toString(),
            result.sessionVersion(),
            result.reviewedAt()
        );
    }
}
