package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.service.RegionAdminContentListResult;

public record RegionAdminContentsResponse(
    List<Content> contents
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public RegionAdminContentsResponse {
        contents = List.copyOf(contents);
    }

    public static RegionAdminContentsResponse from(RegionAdminContentListResult result) {
        return new RegionAdminContentsResponse(
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
        Instant approvedAt,
        Operator operator,
        String representativeImageUrl,
        Instant representativeImageUrlExpiresAt
    ) {

        private static Content from(RegionAdminContentListResult.Content content) {
            return new Content(
                content.contentId().toString(),
                content.contentType().name(),
                content.title(),
                content.status().name(),
                toSeoulOffsetDateTime(content.publishAt()),
                content.submittedAt(),
                content.approvedAt(),
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
