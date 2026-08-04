package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.service.RejectContentResult;

public record RejectContentResponse(
    Long contentId,
    String status,
    Instant rejectedAt
) {

    public static RejectContentResponse from(RejectContentResult result) {
        return new RejectContentResponse(
            result.contentId(),
            result.status().name(),
            result.rejectedAt()
        );
    }
}
