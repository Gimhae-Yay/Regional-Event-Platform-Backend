package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;

public record ApproveContentSessionResult(
    Long sessionId,
    Long contentId,
    ContentSessionStatus status,
    Instant reviewedAt
) {

    public static ApproveContentSessionResult from(ContentSession contentSession) {
        return new ApproveContentSessionResult(
            contentSession.getSessionId(),
            contentSession.getContent().getContentId(),
            contentSession.getStatus(),
            contentSession.getReviewedAt()
        );
    }
}
