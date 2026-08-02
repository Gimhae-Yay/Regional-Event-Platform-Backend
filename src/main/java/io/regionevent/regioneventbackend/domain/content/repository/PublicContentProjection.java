package io.regionevent.regioneventbackend.domain.content.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;

public record PublicContentProjection(
    Long contentId,
    ContentType contentType,
    String title,
    String locationText,
    ImageObject representativeImageObject,
    Instant representativeImageAssignedAt,
    boolean reservationAvailable
) {
}
