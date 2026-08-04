package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.service.MyContentListResult;

public record GetMyContentsResponse(List<ContentResponse> contents) {

    public GetMyContentsResponse {
        contents = List.copyOf(contents);
    }

    public static GetMyContentsResponse from(MyContentListResult result) {
        List<ContentResponse> contents = result.contents().stream()
            .map(ContentResponse::from)
            .toList();
        return new GetMyContentsResponse(contents);
    }

    public record ContentResponse(
        String contentId,
        ContentType contentType,
        String title,
        ContentStatus status,
        Instant createdAt
    ) {

        private static ContentResponse from(MyContentListResult.Content content) {
            return new ContentResponse(
                content.contentId().toString(),
                content.contentType(),
                content.title(),
                content.status(),
                content.createdAt()
            );
        }
    }
}
