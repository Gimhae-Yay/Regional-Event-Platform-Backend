package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.service.DeleteContentResult;

public record DeleteContentResponse(
    String contentId,
    String deletionEventStatus,
    Instant deletedAt,
    String deletionReason
) {

    public static DeleteContentResponse from(DeleteContentResult result) {
        return new DeleteContentResponse(
            result.contentId().toString(),
            result.deletionEventStatus().name(),
            result.deletedAt(),
            result.deletionReason()
        );
    }
}
