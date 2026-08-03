package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;

public record CreateContentResponse(
    String contentId,
    ContentType contentType,
    ContentStatus status,
    Instant submittedAt
) {
}
