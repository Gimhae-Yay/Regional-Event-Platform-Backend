package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;

public record RegionAdminContentListResult(
    ContentStatus status,
    List<Content> contents
) {

    public RegionAdminContentListResult {
        contents = List.copyOf(contents);
    }

    public record Content(
        Long contentId,
        ContentType contentType,
        String title,
        ContentStatus status,
        Instant publishAt,
        Instant submittedAt,
        Instant approvedAt,
        Long operatorId,
        String operatorName,
        String representativeImageUrl,
        Instant representativeImageUrlExpiresAt
    ) {
    }
}
