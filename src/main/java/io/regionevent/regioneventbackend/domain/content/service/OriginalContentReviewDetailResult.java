package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;

public record OriginalContentReviewDetailResult(
    Long contentId,
    Long regionId,
    Long operatorId,
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
    List<Session> sessions
) {

    public OriginalContentReviewDetailResult {
        sessions = List.copyOf(sessions);
    }

    public record Session(
        Long sessionId,
        ContentSessionStatus status,
        Instant startsAt,
        Instant endsAt,
        Instant checkinOpenAt,
        Instant checkinCloseAt,
        int capacity,
        int remainingCapacity
    ) {
    }
}
