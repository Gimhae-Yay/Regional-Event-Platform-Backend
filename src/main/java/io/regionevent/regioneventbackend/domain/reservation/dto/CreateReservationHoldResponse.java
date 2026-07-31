package io.regionevent.regioneventbackend.domain.reservation.dto;

import java.time.Instant;

public record CreateReservationHoldResponse(
    String holdId,
    String sessionId,
    int quantity,
    String status,
    Instant expiresAt,
    Instant createdAt
) {
}
