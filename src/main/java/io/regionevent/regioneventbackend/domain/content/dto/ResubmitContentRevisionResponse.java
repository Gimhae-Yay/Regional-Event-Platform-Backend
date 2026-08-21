package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.service.ResubmitContentRevisionResult;

public record ResubmitContentRevisionResponse(
    String revisionId,
    String sourceRevisionId,
    String contentId,
    ContentRevisionStatus status,
    int baseContentVersion,
    Instant submittedAt
) {

    public static ResubmitContentRevisionResponse from(ResubmitContentRevisionResult result) {
        return new ResubmitContentRevisionResponse(
            result.revisionId().toString(),
            result.sourceRevisionId().toString(),
            result.contentId().toString(),
            result.status(),
            result.baseContentVersion(),
            result.submittedAt()
        );
    }
}
