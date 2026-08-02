package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentType;

public record PublicContentListResult(List<Content> contents) {

    public PublicContentListResult {
        contents = List.copyOf(contents);
    }

    public record Content(
        Long contentId,
        ContentType contentType,
        String title,
        String locationText,
        String representativeImageUrl,
        Instant representativeImageUrlExpiresAt,
        boolean reservationAvailable
    ) {
    }
}
