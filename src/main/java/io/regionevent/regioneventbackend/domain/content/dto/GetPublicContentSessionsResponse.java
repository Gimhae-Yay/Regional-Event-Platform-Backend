package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;

public record GetPublicContentSessionsResponse(
    String contentId,
    List<SessionResponse> sessions
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public GetPublicContentSessionsResponse {
        sessions = List.copyOf(sessions);
    }

    public static GetPublicContentSessionsResponse from(
        Long contentId,
        List<ContentSession> contentSessions
    ) {
        List<SessionResponse> sessions = contentSessions.stream()
            .map(SessionResponse::from)
            .toList();
        return new GetPublicContentSessionsResponse(contentId.toString(), sessions);
    }

    public record SessionResponse(
        String sessionId,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt
    ) {

        private static SessionResponse from(ContentSession contentSession) {
            return new SessionResponse(
                contentSession.getSessionId().toString(),
                contentSession.getStartsAt().atZone(SEOUL_TIME_ZONE).toOffsetDateTime(),
                contentSession.getEndsAt().atZone(SEOUL_TIME_ZONE).toOffsetDateTime()
            );
        }
    }
}
