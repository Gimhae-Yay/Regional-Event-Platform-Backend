package io.regionevent.regioneventbackend.domain.content.dto;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;

public record UpdateMyContentResponse(
    String contentId,
    ContentStatus status
) {

    public static UpdateMyContentResponse from(Content content) {
        return new UpdateMyContentResponse(
            content.getContentId().toString(),
            content.getStatus()
        );
    }
}
