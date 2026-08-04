package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.List;

public record PendingContentSessionListResult(List<Session> sessions) {

    public PendingContentSessionListResult {
        sessions = List.copyOf(sessions);
    }

    public record Session(
        Long sessionId,
        Long contentId,
        String contentTitle,
        String status,
        Instant startsAt,
        Instant endsAt,
        Instant checkinOpenAt,
        Instant checkinCloseAt,
        int capacity,
        Instant createdAt,
        Long operatorId,
        String operatorName
    ) {
    }
}
