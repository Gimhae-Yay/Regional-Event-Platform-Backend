package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.service.SuspendContentResult;

public record SuspendContentResponse(
    String contentId,
    String status,
    Instant suspendedAt,
    String suspensionReason
) {

    public static SuspendContentResponse from(SuspendContentResult result) {
        return new SuspendContentResponse(
            result.contentId().toString(),
            result.status().name(),
            result.suspendedAt(),
            result.suspensionReason()
        );
    }
}
