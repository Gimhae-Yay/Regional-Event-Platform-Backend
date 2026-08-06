package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;

public record CreateSessionRevisionResult(
    Long revisionId,
    SessionRevisionStatus status,
    Long contentId,
    Long targetSessionId,
    int baseSessionVersion,
    Instant startsAt,
    Instant endsAt,
    Instant checkinOpenAt,
    Instant checkinCloseAt,
    int capacity,
    Instant requestedAt
) {

    public static CreateSessionRevisionResult from(SessionRevision revision) {
        return new CreateSessionRevisionResult(
            revision.getSessionRevisionId(),
            revision.getStatus(),
            revision.getContent().getContentId(),
            revision.getTargetSession().getSessionId(),
            revision.getBaseSessionVersion(),
            revision.getStartsAt(),
            revision.getEndsAt(),
            revision.getCheckinOpenAt(),
            revision.getCheckinCloseAt(),
            revision.getCapacity(),
            revision.getSubmittedAt()
        );
    }
}
