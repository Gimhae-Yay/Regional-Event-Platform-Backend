package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.service.PendingContentRevisionListResult;

public record PendingContentRevisionsResponse(
    List<Revision> revisions
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public PendingContentRevisionsResponse {
        revisions = List.copyOf(revisions);
    }

    public static PendingContentRevisionsResponse from(PendingContentRevisionListResult result) {
        return new PendingContentRevisionsResponse(
            result.revisions().stream()
                .map(Revision::from)
                .toList()
        );
    }

    public record Revision(
        String revisionId,
        String contentId,
        String reviewType,
        String contentStatus,
        String title,
        OffsetDateTime candidatePublishAt,
        Instant submittedAt,
        Operator operator,
        String representativeImageUrl,
        Instant representativeImageUrlExpiresAt
    ) {

        private static Revision from(PendingContentRevisionListResult.Revision revision) {
            return new Revision(
                revision.revisionId().toString(),
                revision.contentId().toString(),
                revision.reviewType().name(),
                revision.contentStatus().name(),
                revision.title(),
                toSeoulOffsetDateTime(revision.candidatePublishAt()),
                revision.submittedAt(),
                new Operator(revision.operatorId().toString(), revision.operatorName()),
                revision.representativeImageUrl(),
                revision.representativeImageUrlExpiresAt()
            );
        }
    }

    public record Operator(
        String operatorId,
        String name
    ) {
    }

    private static OffsetDateTime toSeoulOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atZone(SEOUL_TIME_ZONE).toOffsetDateTime();
    }
}
