package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.service.PendingSessionRevisionListResult;

public record PendingSessionRevisionsResponse(
    List<Revision> revisions
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public PendingSessionRevisionsResponse {
        revisions = List.copyOf(revisions);
    }

    public static PendingSessionRevisionsResponse from(PendingSessionRevisionListResult result) {
        return new PendingSessionRevisionsResponse(
            result.revisions().stream()
                .map(Revision::from)
                .toList()
        );
    }

    public record Revision(
        String revisionId,
        String contentId,
        String contentTitle,
        String targetSessionId,
        int baseSessionVersion,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime checkinOpenAt,
        OffsetDateTime checkinCloseAt,
        int capacity,
        Instant submittedAt,
        Operator operator
    ) {

        private static Revision from(PendingSessionRevisionListResult.Revision revision) {
            return new Revision(
                revision.revisionId().toString(),
                revision.contentId().toString(),
                revision.contentTitle(),
                revision.targetSessionId().toString(),
                revision.baseSessionVersion(),
                toSeoulOffsetDateTime(revision.startsAt()),
                toSeoulOffsetDateTime(revision.endsAt()),
                toSeoulOffsetDateTime(revision.checkinOpenAt()),
                toSeoulOffsetDateTime(revision.checkinCloseAt()),
                revision.capacity(),
                revision.submittedAt(),
                new Operator(revision.operatorId().toString(), revision.operatorName())
            );
        }
    }

    public record Operator(
        String operatorId,
        String name
    ) {
    }

    private static OffsetDateTime toSeoulOffsetDateTime(Instant instant) {
        return instant.atZone(SEOUL_TIME_ZONE).toOffsetDateTime();
    }
}
