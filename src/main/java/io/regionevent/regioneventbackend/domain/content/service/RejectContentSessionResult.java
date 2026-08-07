package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;

public record RejectContentSessionResult(
    Long sessionId,
    Long contentId,
    ContentSessionStatus status,
    String rejectReason,
    Instant reviewedAt
) {

    public static RejectContentSessionResult from(ContentSession contentSession) {
        return new RejectContentSessionResult(
            contentSession.getSessionId(),
            contentSession.getContent().getContentId(),
            contentSession.getStatus(),
            contentSession.getRejectReason(),
            contentSession.getReviewedAt()
        );
    }
}
