package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.service.PendingContentSessionListResult;

public record PendingContentSessionsResponse(List<Session> sessions) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public PendingContentSessionsResponse {
        sessions = List.copyOf(sessions);
    }

    public static PendingContentSessionsResponse from(PendingContentSessionListResult result) {
        return new PendingContentSessionsResponse(result.sessions().stream().map(Session::from).toList());
    }

    public record Session(
        String sessionId,
        String contentId,
        String contentTitle,
        String status,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime checkinOpenAt,
        OffsetDateTime checkinCloseAt,
        int capacity,
        Instant createdAt,
        Operator operator
    ) {

        private static Session from(PendingContentSessionListResult.Session session) {
            return new Session(
                session.sessionId().toString(),
                session.contentId().toString(),
                session.contentTitle(),
                session.status(),
                toSeoulOffsetDateTime(session.startsAt()),
                toSeoulOffsetDateTime(session.endsAt()),
                toSeoulOffsetDateTime(session.checkinOpenAt()),
                toSeoulOffsetDateTime(session.checkinCloseAt()),
                session.capacity(),
                session.createdAt(),
                new Operator(session.operatorId().toString(), session.operatorName())
            );
        }
    }

    public record Operator(String operatorId, String name) {
    }

    private static OffsetDateTime toSeoulOffsetDateTime(Instant instant) {
        return instant.atZone(SEOUL_TIME_ZONE).toOffsetDateTime();
    }
}
