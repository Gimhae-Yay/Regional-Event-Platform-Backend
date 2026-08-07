package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;

public record ApproveSessionRevisionResult(
    Long revisionId,
    SessionRevisionStatus revisionStatus,
    Long contentId,
    Long targetSessionId,
    int sessionVersion,
    Instant reviewedAt
) {

    public static ApproveSessionRevisionResult from(
        SessionRevision revision,
        ContentSession contentSession
    ) {
        return new ApproveSessionRevisionResult(
            revision.getSessionRevisionId(),
            revision.getStatus(),
            revision.getContent().getContentId(),
            contentSession.getSessionId(),
            contentSession.getVersionNo(),
            revision.getReviewedAt()
        );
    }
}
