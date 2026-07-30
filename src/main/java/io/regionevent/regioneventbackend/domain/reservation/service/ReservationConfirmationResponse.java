package io.regionevent.regioneventbackend.domain.reservation.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;

public record ReservationConfirmationResponse(
    String reservationId,
    String reservationNo,
    String holdId,
    String sessionId,
    ReservationStatus status,
    Instant confirmedAt
) {

    public static ReservationConfirmationResponse from(Reservation reservation) {
        return new ReservationConfirmationResponse(
            reservation.getReservationId().toString(),
            reservation.getReservationNo(),
            reservation.getCapacityHold().getHoldId().toString(),
            reservation.getContentSession().getSessionId().toString(),
            reservation.getStatus(),
            reservation.getConfirmedAt()
        );
    }
}
