package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;

public record MyContentDetailResult(
    Long contentId,
    ContentType contentType,
    ContentStatus status,
    String title,
    String description,
    String representativeImageUrl,
    Instant representativeImageUrlExpiresAt,
    String locationText,
    String operatingHoursText,
    String contactText,
    String precautions,
    String ageRequirement,
    String materials,
    String cancellationPolicyText,
    Instant publishAt,
    String rejectionReason,
    Instant createdAt,
    Instant updatedAt
) {
}
