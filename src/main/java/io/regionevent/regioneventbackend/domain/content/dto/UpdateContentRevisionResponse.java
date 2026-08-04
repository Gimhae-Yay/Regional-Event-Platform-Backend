package io.regionevent.regioneventbackend.domain.content.dto;

import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;

public record UpdateContentRevisionResponse(
    String revisionId,
    String contentId,
    ContentRevisionStatus status
) {

    public static UpdateContentRevisionResponse from(ContentRevision contentRevision) {
        return new UpdateContentRevisionResponse(
            contentRevision.getContentRevisionId().toString(),
            contentRevision.getContent().getContentId().toString(),
            contentRevision.getStatus()
        );
    }
}
