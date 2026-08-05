package io.regionevent.regioneventbackend.domain.region.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.region.service.RegionHomeResult;
import io.regionevent.regioneventbackend.domain.region.service.PublicRegionStaticInfo;

public record GetRegionHomeResponse(
    RegionResponse region,
    List<ContentResponse> ongoingContents,
    List<ContentResponse> upcomingContents
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public GetRegionHomeResponse {
        ongoingContents = List.copyOf(ongoingContents);
        upcomingContents = List.copyOf(upcomingContents);
    }

    public static GetRegionHomeResponse from(RegionHomeResult result) {
        return new GetRegionHomeResponse(
            RegionResponse.from(result.region()),
            result.ongoingContents().stream().map(ContentResponse::from).toList(),
            result.upcomingContents().stream().map(ContentResponse::from).toList()
        );
    }

    private static OffsetDateTime toSeoulOffsetDateTime(Instant instant) {
        return instant.atZone(SEOUL_TIME_ZONE).toOffsetDateTime();
    }

    public record RegionResponse(
        String regionId,
        String regionCode,
        String name
    ) {

        private static RegionResponse from(PublicRegionStaticInfo region) {
            return new RegionResponse(
                region.regionId().toString(),
                region.regionCode(),
                region.name()
            );
        }
    }

    public record ContentResponse(
        String contentId,
        ContentType contentType,
        String title,
        String locationText,
        String representativeImageUrl,
        Instant representativeImageUrlExpiresAt,
        boolean reservationAvailable,
        DisplaySessionResponse displaySession
    ) {

        private static ContentResponse from(RegionHomeResult.Content content) {
            return new ContentResponse(
                content.contentId().toString(),
                content.contentType(),
                content.title(),
                content.locationText(),
                content.representativeImageUrl(),
                content.representativeImageUrlExpiresAt(),
                content.reservationAvailable(),
                DisplaySessionResponse.from(content.displaySession())
            );
        }
    }

    public record DisplaySessionResponse(
        String sessionId,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        int remainingCapacity
    ) {

        private static DisplaySessionResponse from(RegionHomeResult.DisplaySession displaySession) {
            return new DisplaySessionResponse(
                displaySession.sessionId().toString(),
                toSeoulOffsetDateTime(displaySession.startsAt()),
                toSeoulOffsetDateTime(displaySession.endsAt()),
                displaySession.remainingCapacity()
            );
        }
    }
}
