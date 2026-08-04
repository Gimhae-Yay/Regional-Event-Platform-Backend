package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;

public record DeleteContentResult(
    Long contentId,
    ContentLogStatus deletionEventStatus,
    Instant deletedAt,
    String deletionReason
) {

    public static DeleteContentResult from(Content content, Instant deletedAt, String deletionReason) {
        return new DeleteContentResult(
            content.getContentId(),
            ContentLogStatus.DELETED,
            deletedAt,
            deletionReason
        );
    }
}
