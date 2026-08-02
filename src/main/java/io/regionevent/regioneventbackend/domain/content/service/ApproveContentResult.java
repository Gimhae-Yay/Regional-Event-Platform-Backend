package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;

public record ApproveContentResult(
    Long contentId,
    ContentStatus status,
    Instant publishAt,
    Instant approvedAt
) {

    public static ApproveContentResult from(Content content, Instant approvedAt) {
        return new ApproveContentResult(
            content.getContentId(),
            content.getStatus(),
            content.getPublishAt(),
            approvedAt
        );
    }
}
