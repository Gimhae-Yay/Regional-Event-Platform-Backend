package io.regionevent.regioneventbackend.domain.reservation.service;

import java.time.Instant;

public record MyReservationQrResult(
    Long reservationId,
    Long sessionId,
    String qrToken,
    Instant issuedAt,
    Instant expiresAt,
    Instant checkinClosesAt
) {
}
