package io.regionevent.regioneventbackend.domain.content.repository;

import io.regionevent.regioneventbackend.domain.content.entity.ContentType;

public record PublicContentStaticProjection(
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
    String cancellationPolicyText
) {
}
