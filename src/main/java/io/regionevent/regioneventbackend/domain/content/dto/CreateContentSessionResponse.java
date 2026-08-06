package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.service.CreateContentSessionResult;

public record CreateContentSessionResponse(
    String sessionId,
    String contentId,
    ContentSessionStatus status,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    OffsetDateTime checkinOpenAt,
    OffsetDateTime checkinCloseAt,
    int capacity,
    int remainingCapacity,
    Instant createdAt
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public static CreateContentSessionResponse from(CreateContentSessionResult result) {
        return new CreateContentSessionResponse(
            result.sessionId().toString(),
            result.contentId().toString(),
            result.status(),
            toSeoulOffsetDateTime(result.startsAt()),
            toSeoulOffsetDateTime(result.endsAt()),
            toSeoulOffsetDateTime(result.checkinOpenAt()),
            toSeoulOffsetDateTime(result.checkinCloseAt()),
            result.capacity(),
            result.remainingCapacity(),
            result.createdAt()
        );
    }

    private static OffsetDateTime toSeoulOffsetDateTime(Instant instant) {
        return instant.atZone(SEOUL_TIME_ZONE).toOffsetDateTime();
    }
}
