package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.service.SubmitContentResult;

public record SubmitContentResponse(
    String contentId,
    String status,
    Instant submittedAt
) {

    public static SubmitContentResponse from(SubmitContentResult result) {
        return new SubmitContentResponse(
            result.contentId().toString(),
            result.status().name(),
            result.submittedAt()
        );
    }
}
