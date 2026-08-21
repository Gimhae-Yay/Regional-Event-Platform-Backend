package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;

public record LatestContentRevisionDetailResult(
    Long revisionId,
    Long contentId,
    int revisionNo,
    int baseContentVersion,
    ContentRevisionStatus status,
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
    long reservationPrice,
    Instant publishAt,
    String reviewReason,
    Instant submittedAt,
    Instant reviewedAt
) {
}
