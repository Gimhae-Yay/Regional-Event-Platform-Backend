package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.List;

public record PendingSessionRevisionListResult(
    List<Revision> revisions
) {

    public PendingSessionRevisionListResult {
        revisions = List.copyOf(revisions);
    }

    public record Revision(
        Long revisionId,
        Long contentId,
        String contentTitle,
        Long targetSessionId,
        int baseSessionVersion,
        Instant startsAt,
        Instant endsAt,
        Instant checkinOpenAt,
        Instant checkinCloseAt,
        int capacity,
        Instant submittedAt,
        Long operatorId,
        String operatorName
    ) {
    }
}
