package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;

public record OperatorContentSessionListResult(
    Long contentId,
    List<Session> sessions
) {

    public OperatorContentSessionListResult {
        sessions = List.copyOf(sessions);
    }

    public record Session(
        Long sessionId,
        ContentSessionStatus status,
        int version,
        Instant startsAt,
        Instant endsAt,
        Instant checkinOpenAt,
        Instant checkinCloseAt,
        int capacity,
        int remainingCapacity,
        String rejectReason,
        Instant cancelledAt,
        String cancellationReason,
        Instant completedAt,
        Instant createdAt,
        PendingChangeRequest pendingChangeRequest
    ) {
    }

    public record PendingChangeRequest(
        Long revisionId,
        SessionRevisionStatus status,
        int baseSessionVersion,
        Candidate candidate,
        Instant submittedAt
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
}
