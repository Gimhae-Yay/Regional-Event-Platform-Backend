package io.regionevent.regioneventbackend.domain.stampbook.repository;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;

public record StampbookReviewTargetContentProjection(
    Long contentId,
    Long regionId,
    String title,
    ContentStatus status
) {
}
