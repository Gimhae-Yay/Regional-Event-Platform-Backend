package io.regionevent.regioneventbackend.domain.reservation.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;

public record ConfirmReservationResponse(
    String reservationId,
    String reservationNo,
    String holdId,
    String sessionId,
    String status,
    Instant confirmedAt
) {

    public static ConfirmReservationResponse from(Reservation reservation) {
        return new ConfirmReservationResponse(
            reservation.getReservationId().toString(),
            reservation.getReservationNo(),
            reservation.getCapacityHold().getHoldId().toString(),
            reservation.getContentSession().getSessionId().toString(),
            reservation.getStatus().name(),
            reservation.getConfirmedAt()
        );
    }
}
