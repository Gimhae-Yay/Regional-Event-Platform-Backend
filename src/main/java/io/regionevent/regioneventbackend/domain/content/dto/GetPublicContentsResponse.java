package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.service.PublicContentListResult;

public record GetPublicContentsResponse(List<ContentResponse> contents) {

    public GetPublicContentsResponse {
        contents = List.copyOf(contents);
    }

    public static GetPublicContentsResponse from(PublicContentListResult result) {
        List<ContentResponse> contents = result.contents().stream()
            .map(ContentResponse::from)
            .toList();
        return new GetPublicContentsResponse(contents);
    }

    public record ContentResponse(
        String contentId,
        ContentType contentType,
        String title,
        String locationText,
        String representativeImageUrl,
        Instant representativeImageUrlExpiresAt,
        boolean reservationAvailable
    ) {

        private static ContentResponse from(PublicContentListResult.Content content) {
            return new ContentResponse(
                content.contentId().toString(),
                content.contentType(),
                content.title(),
                content.locationText(),
                content.representativeImageUrl(),
                content.representativeImageUrlExpiresAt(),
                content.reservationAvailable()
            );
        }
    }
}
