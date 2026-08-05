package io.regionevent.regioneventbackend.domain.content.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;

public record RegionHomeContentSessionVerificationProjection(
    Long regionId,
    Long contentId,
    int versionNo,
    ImageObject representativeImageObject,
    Instant representativeImageAssignedAt,
    Long sessionId,
    Instant startsAt,
    Instant endsAt,
    int remainingCapacity,
    boolean reservationAvailable,
    boolean ongoing
) {
}
