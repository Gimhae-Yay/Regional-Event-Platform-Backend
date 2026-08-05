package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.service.RejectContentSessionResult;

public record RejectContentSessionResponse(
    String sessionId,
    String contentId,
    String status,
    String rejectReason,
    Instant reviewedAt
) {

    public static RejectContentSessionResponse from(RejectContentSessionResult result) {
        return new RejectContentSessionResponse(
            result.sessionId().toString(),
            result.contentId().toString(),
            result.status().name(),
            result.rejectReason(),
            result.reviewedAt()
        );
    }
}
