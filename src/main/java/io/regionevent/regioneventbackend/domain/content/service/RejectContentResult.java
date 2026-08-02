package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;

public record RejectContentResult(
    Long contentId,
    ContentStatus status,
    Instant rejectedAt
) {

    public static RejectContentResult from(Content content, Instant rejectedAt) {
        return new RejectContentResult(
            content.getContentId(),
            content.getStatus(),
            rejectedAt
        );
    }
}
