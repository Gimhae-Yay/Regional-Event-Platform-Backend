package io.regionevent.regioneventbackend.domain.content.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;

public record PublicContentDetailVerificationProjection(
    Long regionId,
    Long contentId,
    int versionNo,
    ImageObject representativeImageObject,
    Instant representativeImageAssignedAt,
    String contactText
) {
}
