package io.regionevent.regioneventbackend.domain.reservation.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.reservation.service.MyReservationQrResult;

public record GetMyReservationQrResponse(
    Long reservationId,
    Long sessionId,
    String qrToken,
    Instant issuedAt,
    Instant expiresAt,
    Instant checkinClosesAt
) {

    public static GetMyReservationQrResponse from(MyReservationQrResult result) {
        return new GetMyReservationQrResponse(
            result.reservationId(),
            result.sessionId(),
            result.qrToken(),
            result.issuedAt(),
            result.expiresAt(),
            result.checkinClosesAt()
        );
    }
}
