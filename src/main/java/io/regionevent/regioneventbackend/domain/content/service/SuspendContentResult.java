package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;

public record SuspendContentResult(
    Long contentId,
    ContentStatus status,
    Instant suspendedAt,
    String suspensionReason
) {

    public static SuspendContentResult from(Content content, ContentLog suspendedLog) {
        return new SuspendContentResult(
            content.getContentId(),
            content.getStatus(),
            suspendedLog.getDate(),
            suspendedLog.getReason()
        );
    }
}
