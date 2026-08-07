package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;

public record CreateContentSessionResult(
    Long sessionId,
    Long contentId,
    ContentSessionStatus status,
    Instant startsAt,
    Instant endsAt,
    Instant checkinOpenAt,
    Instant checkinCloseAt,
    int capacity,
    int remainingCapacity,
    Instant createdAt
) {

    public static CreateContentSessionResult from(ContentSession contentSession) {
        return new CreateContentSessionResult(
            contentSession.getSessionId(),
            contentSession.getContent().getContentId(),
            contentSession.getStatus(),
            contentSession.getStartsAt(),
            contentSession.getEndsAt(),
            contentSession.getCheckinOpenAt(),
            contentSession.getCheckinCloseAt(),
            contentSession.getCapacity(),
            contentSession.getRemainingCapacity(),
            contentSession.getCreatedAt()
        );
    }
}
