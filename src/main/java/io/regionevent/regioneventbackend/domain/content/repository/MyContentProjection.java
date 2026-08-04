package io.regionevent.regioneventbackend.domain.content.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;

public record MyContentProjection(
    Long contentId,
    ContentType contentType,
    String title,
    ContentStatus status,
    Instant createdAt
) {
}
