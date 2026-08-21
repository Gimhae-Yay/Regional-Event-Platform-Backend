package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;

public record ResubmitContentRevisionResult(
    Long revisionId,
    Long sourceRevisionId,
    Long contentId,
    ContentRevisionStatus status,
    int baseContentVersion,
    Instant submittedAt
) {

    public static ResubmitContentRevisionResult from(
        ContentRevision sourceRevision,
        ContentRevision resubmittedRevision
    ) {
        return new ResubmitContentRevisionResult(
            resubmittedRevision.getContentRevisionId(),
            sourceRevision.getContentRevisionId(),
            resubmittedRevision.getContent().getContentId(),
            resubmittedRevision.getStatus(),
            resubmittedRevision.getBaseContentVersion(),
            resubmittedRevision.getSubmittedAt()
        );
    }
}
