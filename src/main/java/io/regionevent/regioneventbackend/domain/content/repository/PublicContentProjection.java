package io.regionevent.regioneventbackend.domain.content.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;

public record PublicContentProjection(
    Long regionId,
    Long contentId,
    int versionNo,
    ContentType contentType,
    String title,
    String description,
    String locationText,
    String operatingHoursText,
    String precautions,
    String ageRequirement,
    String materials,
    String cancellationPolicyText,
    ImageObject representativeImageObject,
    Instant representativeImageAssignedAt,
    boolean reservationAvailable
) {
}
