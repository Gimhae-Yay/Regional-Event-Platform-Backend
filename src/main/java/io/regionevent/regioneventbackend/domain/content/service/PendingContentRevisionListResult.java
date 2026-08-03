package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;

public record PendingContentRevisionListResult(
    List<Revision> revisions
) {

    public PendingContentRevisionListResult {
        revisions = List.copyOf(revisions);
    }

    public record Revision(
        Long revisionId,
        Long contentId,
        ContentRevisionReviewType reviewType,
        ContentStatus contentStatus,
        String title,
        Instant candidatePublishAt,
        Instant submittedAt,
        Long operatorId,
        String operatorName,
        String representativeImageUrl,
        Instant representativeImageUrlExpiresAt
    ) {
    }
}
