package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;

public record ApproveContentRevisionResult(
    Long revisionId,
    Long contentId,
    ContentRevisionStatus revisionStatus,
    ContentStatus contentStatus,
    long reservationPrice,
    Instant publishAt,
    Instant reviewedAt
) {

    public static ApproveContentRevisionResult from(ContentRevision revision) {
        return new ApproveContentRevisionResult(
            revision.getContentRevisionId(),
            revision.getContent().getContentId(),
            revision.getStatus(),
            revision.getContent().getStatus(),
            revision.getContent().getReservationPrice(),
            revision.getContent().getPublishAt(),
            revision.getReviewedAt()
        );
    }
}
