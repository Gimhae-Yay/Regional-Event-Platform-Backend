package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.service.CancelContentSessionResult;

public record CancelContentSessionResponse(
    String sessionId,
    String status,
    String cancellationReason,
    Instant cancelledAt
) {

    public static CancelContentSessionResponse from(CancelContentSessionResult result) {
        return new CancelContentSessionResponse(
            result.sessionId().toString(),
            result.status().name(),
            result.cancellationReason(),
            result.cancelledAt()
        );
    }
}
