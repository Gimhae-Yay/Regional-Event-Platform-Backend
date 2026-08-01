package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;

public record CreateContentRevisionResponse(
    String revisionId,
    String contentId,
    ContentRevisionStatus status,
    int baseContentVersion,
    Instant submittedAt
) {

    public static CreateContentRevisionResponse from(ContentRevision contentRevision) {
        return new CreateContentRevisionResponse(
            contentRevision.getContentRevisionId().toString(),
            contentRevision.getContent().getContentId().toString(),
            contentRevision.getStatus(),
            contentRevision.getBaseContentVersion(),
            contentRevision.getSubmittedAt()
        );
    }
}
