package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.service.CreateSessionRevisionResult;

public record CreateSessionRevisionResponse(
    String revisionId,
    SessionRevisionStatus status,
    String contentId,
    String targetSessionId,
    int baseSessionVersion,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    OffsetDateTime checkinOpenAt,
    OffsetDateTime checkinCloseAt,
    int capacity,
    Instant requestedAt
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public static CreateSessionRevisionResponse from(CreateSessionRevisionResult result) {
        return new CreateSessionRevisionResponse(
            result.revisionId().toString(),
            result.status(),
            result.contentId().toString(),
            result.targetSessionId().toString(),
            result.baseSessionVersion(),
            toSeoulOffsetDateTime(result.startsAt()),
            toSeoulOffsetDateTime(result.endsAt()),
            toSeoulOffsetDateTime(result.checkinOpenAt()),
            toSeoulOffsetDateTime(result.checkinCloseAt()),
            result.capacity(),
            result.requestedAt()
        );
    }

    private static OffsetDateTime toSeoulOffsetDateTime(Instant instant) {
        return instant.atZone(SEOUL_TIME_ZONE).toOffsetDateTime();
    }
}
