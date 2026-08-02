package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;

public record ContentRevisionReviewDetailResult(
    Long revisionId,
    Long contentId,
    ContentRevisionReviewType reviewType,
    ContentStatus contentStatus,
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
    Instant candidatePublishAt,
    List<Session> sessions,
    Instant submittedAt
) {

    public ContentRevisionReviewDetailResult {
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
