package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.service.ApproveContentSessionResult;

public record ApproveContentSessionResponse(
    String sessionId,
    String contentId,
    String status,
    Instant reviewedAt
) {

    public static ApproveContentSessionResponse from(ApproveContentSessionResult result) {
        return new ApproveContentSessionResponse(
            result.sessionId().toString(),
            result.contentId().toString(),
            result.status().name(),
            result.reviewedAt()
        );
    }
}
