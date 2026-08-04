package io.regionevent.regioneventbackend.domain.reservation.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;

public record ReservationReadSnapshot(
    ReservationInfo reservation,
    SessionInfo session,
    ContentInfo content,
    ParticipantInfo participant
) {

    public record ReservationInfo(
        Long reservationId,
        String reservationNo,
        ReservationStatus status,
        Instant confirmedAt,
        Instant cancelledAt,
        String cancellationReason,
        Instant expiredAt,
        Long regionId
    ) {
    }

    public record SessionInfo(
        Long sessionId,
        ContentSessionStatus status,
        Instant startsAt,
        Instant endsAt,
        Instant checkinOpenAt,
        Instant checkinCloseAt,
        Long regionId
    ) {
    }

    public record ContentInfo(
        Long contentId,
        String title,
        Long regionId
    ) {
    }

    public record ParticipantInfo(
        Long userId,
        String name,
        String phone
    ) {
    }

    public record VisitInfo(
        Long visitId,
        Long reservationId,
        Long regionId,
        Long sessionId,
        Long contentId,
        Long participantUserId,
        Instant checkedAt
    ) {
    }
}
