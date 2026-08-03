package io.regionevent.regioneventbackend.domain.visit.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.visit.entity.Visit;

public record CheckInResponse(
    String visitId,
    String reservationId,
    String sessionId,
    String reservationStatus,
    String checkInMethod,
    Instant checkedAt
) {

    public static CheckInResponse from(Visit visit) {
        return new CheckInResponse(
            visit.getVisitId().toString(),
            visit.getReservation().getReservationId().toString(),
            visit.getContentSession().getSessionId().toString(),
            visit.getReservation().getStatus().name(),
            visit.getCheckinMethod().name(),
            visit.getCheckedAt()
        );
    }
}
