package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.service.CreateContentSessionResult;

public record CreateContentSessionResponse(
    String sessionId,
    String contentId,
    ContentSessionStatus status,
    Instant startsAt,
    Instant endsAt,
    Instant checkinOpenAt,
    Instant checkinCloseAt,
    int capacity,
    int remainingCapacity,
    Instant createdAt
) {

    public static CreateContentSessionResponse from(CreateContentSessionResult result) {
        return new CreateContentSessionResponse(
            result.sessionId().toString(),
            result.contentId().toString(),
            result.status(),
            result.startsAt(),
            result.endsAt(),
            result.checkinOpenAt(),
            result.checkinCloseAt(),
            result.capacity(),
            result.remainingCapacity(),
            result.createdAt()
        );
    }
}
