package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;

public record RejectContentRevisionResult(
    Long revisionId,
    Long contentId,
    ContentRevisionStatus revisionStatus,
    ContentStatus contentStatus,
    String reviewReason,
    Instant reviewedAt
) {

    public static RejectContentRevisionResult from(ContentRevision revision) {
        return new RejectContentRevisionResult(
            revision.getContentRevisionId(),
            revision.getContent().getContentId(),
            revision.getStatus(),
            revision.getContent().getStatus(),
            revision.getReviewReason(),
            revision.getReviewedAt()
        );
    }
}
