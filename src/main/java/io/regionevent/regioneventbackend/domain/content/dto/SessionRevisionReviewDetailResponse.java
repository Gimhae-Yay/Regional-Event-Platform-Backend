package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import io.regionevent.regioneventbackend.domain.content.service.SessionRevisionReviewDetailResult;

public record SessionRevisionReviewDetailResponse(
    String revisionId,
    String contentId,
    String contentTitle,
    String contentStatus,
    TargetSession targetSession,
    int baseSessionVersion,
    Candidate candidate,
    Instant submittedAt,
    Operator operator
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public static SessionRevisionReviewDetailResponse from(SessionRevisionReviewDetailResult result) {
        return new SessionRevisionReviewDetailResponse(
            result.revisionId().toString(),
            result.contentId().toString(),
            result.contentTitle(),
            result.contentStatus().name(),
            TargetSession.from(result.targetSession()),
            result.baseSessionVersion(),
            Candidate.from(result.candidate()),
            result.submittedAt(),
            Operator.from(result.operator())
        );
    }

    public record TargetSession(
        String sessionId,
        String status,
        int version,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime checkinOpenAt,
        OffsetDateTime checkinCloseAt,
        int capacity,
        int remainingCapacity
    ) {

        private static TargetSession from(SessionRevisionReviewDetailResult.TargetSession session) {
            return new TargetSession(
                session.sessionId().toString(),
                session.status().name(),
                session.version(),
                toSeoulOffsetDateTime(session.startsAt()),
                toSeoulOffsetDateTime(session.endsAt()),
                toSeoulOffsetDateTime(session.checkinOpenAt()),
                toSeoulOffsetDateTime(session.checkinCloseAt()),
                session.capacity(),
                session.remainingCapacity()
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

        private static Candidate from(SessionRevisionReviewDetailResult.Candidate candidate) {
            return new Candidate(
                toSeoulOffsetDateTime(candidate.startsAt()),
                toSeoulOffsetDateTime(candidate.endsAt()),
                toSeoulOffsetDateTime(candidate.checkinOpenAt()),
                toSeoulOffsetDateTime(candidate.checkinCloseAt()),
                candidate.capacity()
            );
        }
    }

    public record Operator(
        String operatorId,
        String name
    ) {

        private static Operator from(SessionRevisionReviewDetailResult.Operator operator) {
            return new Operator(operator.operatorId().toString(), operator.name());
        }
    }

    private static OffsetDateTime toSeoulOffsetDateTime(Instant instant) {
        return instant.atZone(SEOUL_TIME_ZONE).toOffsetDateTime();
    }
}
