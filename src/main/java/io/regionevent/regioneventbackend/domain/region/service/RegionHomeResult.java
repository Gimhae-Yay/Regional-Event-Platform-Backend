package io.regionevent.regioneventbackend.domain.region.service;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentType;

public record RegionHomeResult(
    PublicRegionStaticInfo region,
    List<Content> ongoingContents,
    List<Content> upcomingContents
) {

    public RegionHomeResult {
        ongoingContents = List.copyOf(ongoingContents);
        upcomingContents = List.copyOf(upcomingContents);
    }

    public record Content(
        Long contentId,
        ContentType contentType,
        String title,
        String locationText,
        String representativeImageUrl,
        Instant representativeImageUrlExpiresAt,
        boolean reservationAvailable,
        DisplaySession displaySession
    ) {
    }

    public record DisplaySession(
        Long sessionId,
        Instant startsAt,
        Instant endsAt,
        int remainingCapacity
    ) {
    }
}
