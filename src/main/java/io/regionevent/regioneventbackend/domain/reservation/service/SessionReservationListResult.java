package io.regionevent.regioneventbackend.domain.reservation.service;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;

public record SessionReservationListResult(
    Long contentId,
    SessionInfo session,
    List<SessionReservationReadResult> reservations
) {

    public record SessionInfo(
        Long sessionId,
        ContentSessionStatus status,
        Instant startsAt,
        Instant endsAt,
        Instant checkinOpenAt,
        Instant checkinCloseAt
    ) {
    }
}
