package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;

public record CancelContentSessionResult(
    Long sessionId,
    ContentSessionStatus status,
    String cancellationReason,
    Instant cancelledAt
) {
}
