package io.regionevent.regioneventbackend.domain.reservation.service;

import java.util.UUID;

public record NoShowAndSessionCompletionResult(
    UUID requestId,
    int expiredReservationCount,
    int completedSessionCount,
    int failedSessionCount
) {
}
