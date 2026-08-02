package io.regionevent.regioneventbackend.domain.reservation.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;

public record CancelReservationResponse(
    String reservationId,
    String sessionId,
    String status,
    String cancellationReason,
    Instant cancelledAt,
    Instant capacityReleasedAt
) {

    public static CancelReservationResponse from(Reservation reservation) {
        return new CancelReservationResponse(
            reservation.getReservationId().toString(),
            reservation.getContentSession().getSessionId().toString(),
            reservation.getStatus().name(),
            reservation.getCancellationReason(),
            reservation.getCancelledAt(),
            reservation.getCapacityReleasedAt()
        );
    }
}
