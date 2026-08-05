package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;

public record SessionRevisionReviewDetailResult(
    Long revisionId,
    Long contentId,
    String contentTitle,
    ContentStatus contentStatus,
    TargetSession targetSession,
    int baseSessionVersion,
    Candidate candidate,
    Instant submittedAt,
    Operator operator
) {

    public record TargetSession(
        Long sessionId,
        ContentSessionStatus status,
        int version,
        Instant startsAt,
        Instant endsAt,
        Instant checkinOpenAt,
        Instant checkinCloseAt,
        int capacity,
        int remainingCapacity
    ) {
    }

    public record Candidate(
        Instant startsAt,
        Instant endsAt,
        Instant checkinOpenAt,
        Instant checkinCloseAt,
        int capacity
    ) {
    }

    public record Operator(
        Long operatorId,
        String name
    ) {
    }
}
