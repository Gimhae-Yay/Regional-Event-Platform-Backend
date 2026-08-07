package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;

public record SubmitContentResult(
    Long contentId,
    ContentStatus status,
    Instant submittedAt
) {

    public static SubmitContentResult from(Content content, Instant submittedAt) {
        return new SubmitContentResult(
            content.getContentId(),
            content.getStatus(),
            submittedAt
        );
    }
}
