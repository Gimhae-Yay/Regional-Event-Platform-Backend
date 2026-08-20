package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.service.OperatorContentSessionListResult;

public record GetOperatorContentSessionsResponse(
    String contentId,
    List<Session> sessions
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public GetOperatorContentSessionsResponse {
        sessions = List.copyOf(sessions);
    }

    public static GetOperatorContentSessionsResponse from(OperatorContentSessionListResult result) {
        return new GetOperatorContentSessionsResponse(
            result.contentId().toString(),
            result.sessions().stream()
                .map(Session::from)
                .toList()
        );
    }

    public record Session(
        String sessionId,
        String status,
        int version,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime checkinOpenAt,
        OffsetDateTime checkinCloseAt,
        int capacity,
        int remainingCapacity,
        String rejectReason,
        Instant cancelledAt,
        String cancellationReason,
        Instant completedAt,
        Instant createdAt,
        PendingChangeRequest pendingChangeRequest
    ) {

        private static Session from(OperatorContentSessionListResult.Session session) {
            return new Session(
                session.sessionId().toString(),
                session.status().name(),
                session.version(),
                toSeoulOffsetDateTime(session.startsAt()),
                toSeoulOffsetDateTime(session.endsAt()),
                toSeoulOffsetDateTime(session.checkinOpenAt()),
                toSeoulOffsetDateTime(session.checkinCloseAt()),
                session.capacity(),
                session.remainingCapacity(),
                session.rejectReason(),
                session.cancelledAt(),
                session.cancellationReason(),
                session.completedAt(),
                session.createdAt(),
                PendingChangeRequest.from(session.pendingChangeRequest())
            );
        }
    }

    public record PendingChangeRequest(
        String revisionId,
        String status,
        int baseSessionVersion,
        Candidate candidate,
        Instant submittedAt
    ) {

        private static PendingChangeRequest from(
            OperatorContentSessionListResult.PendingChangeRequest pendingChangeRequest
        ) {
            if (pendingChangeRequest == null) {
                return null;
            }
            return new PendingChangeRequest(
                pendingChangeRequest.revisionId().toString(),
                pendingChangeRequest.status().name(),
                pendingChangeRequest.baseSessionVersion(),
                Candidate.from(pendingChangeRequest.candidate()),
                pendingChangeRequest.submittedAt()
            );
        }
    }

    public record Candidate(
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime checkinOpenAt,
        OffsetDateTime checkinCloseAt,
        int capacity
    ) {

        private static Candidate from(OperatorContentSessionListResult.Candidate candidate) {
            return new Candidate(
                toSeoulOffsetDateTime(candidate.startsAt()),
                toSeoulOffsetDateTime(candidate.endsAt()),
                toSeoulOffsetDateTime(candidate.checkinOpenAt()),
                toSeoulOffsetDateTime(candidate.checkinCloseAt()),
                candidate.capacity()
            );
        }
    }

    private static OffsetDateTime toSeoulOffsetDateTime(Instant instant) {
        return instant.atZone(SEOUL_TIME_ZONE).toOffsetDateTime();
    }
}
