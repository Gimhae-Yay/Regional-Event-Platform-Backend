package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.service.PendingContentListResult;

public record PendingContentsResponse(
    List<Content> contents
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public PendingContentsResponse {
        contents = List.copyOf(contents);
    }

    public static PendingContentsResponse from(PendingContentListResult result) {
        return new PendingContentsResponse(
            result.contents().stream()
                .map(Content::from)
                .toList()
        );
    }

    public record Content(
        String contentId,
        String contentType,
        String title,
        String status,
        OffsetDateTime publishAt,
        Instant submittedAt,
        Operator operator,
        String representativeImageUrl,
        Instant representativeImageUrlExpiresAt
    ) {

        private static Content from(PendingContentListResult.Content content) {
            return new Content(
                content.contentId().toString(),
                content.contentType().name(),
                content.title(),
                content.status().name(),
                toSeoulOffsetDateTime(content.publishAt()),
                content.submittedAt(),
                new Operator(content.operatorId().toString(), content.operatorName()),
                content.representativeImageUrl(),
                content.representativeImageUrlExpiresAt()
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
