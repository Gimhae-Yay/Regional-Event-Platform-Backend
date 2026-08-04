package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.service.ApproveContentRevisionResult;

public record ApproveContentRevisionResponse(
    String revisionId,
    String contentId,
    String revisionStatus,
    String contentStatus,
    Instant publishAt,
    Instant reviewedAt
) {

    public static ApproveContentRevisionResponse from(ApproveContentRevisionResult result) {
        return new ApproveContentRevisionResponse(
            result.revisionId().toString(),
            result.contentId().toString(),
            result.revisionStatus().name(),
            result.contentStatus().name(),
            result.publishAt(),
            result.reviewedAt()
        );
    }
}
