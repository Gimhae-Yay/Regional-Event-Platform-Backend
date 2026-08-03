package io.regionevent.regioneventbackend.domain.reservation.service;

import java.util.UUID;

public record HoldTerminationResult(
    UUID requestId,
    int expiredHoldCount,
    int invalidatedHoldCount,
    int failedHoldCount
) {
}
