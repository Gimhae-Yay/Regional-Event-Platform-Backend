package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;

public record PendingContentListResult(
    List<Content> contents
) {

    public PendingContentListResult {
        contents = List.copyOf(contents);
    }

    public record Content(
        Long contentId,
        ContentType contentType,
        String title,
        ContentStatus status,
        Instant publishAt,
        Instant submittedAt,
        Long operatorId,
        String operatorName,
        String representativeImageUrl,
        Instant representativeImageUrlExpiresAt
    ) {
    }
}
