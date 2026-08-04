package io.regionevent.regioneventbackend.domain.reservation.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationReadSnapshot;

public record ReservationReadProjection(
    Long reservationId,
    String reservationNo,
    ReservationStatus reservationStatus,
    Instant confirmedAt,
    Instant cancelledAt,
    String cancellationReason,
    Instant expiredAt,
    Long reservationRegionId,
    Long sessionId,
    ContentSessionStatus sessionStatus,
    Instant startsAt,
    Instant endsAt,
    Instant checkinOpenAt,
    Instant checkinCloseAt,
    Long sessionRegionId,
    Long contentId,
    String contentTitle,
    Long contentRegionId,
    Long participantUserId,
    String participantName,
    String participantPhone,
    Long visitId,
    Long visitReservationId,
    Long visitRegionId,
    Long visitSessionId,
    Long visitContentId,
    Long visitParticipantUserId,
    Instant checkedAt
) {

    public ReservationReadSnapshot toSnapshot() {
        return new ReservationReadSnapshot(
            new ReservationReadSnapshot.ReservationInfo(
                reservationId,
                reservationNo,
                reservationStatus,
                confirmedAt,
                cancelledAt,
                cancellationReason,
                expiredAt,
                reservationRegionId
            ),
            new ReservationReadSnapshot.SessionInfo(
                sessionId,
                sessionStatus,
                startsAt,
                endsAt,
                checkinOpenAt,
                checkinCloseAt,
                sessionRegionId
            ),
            new ReservationReadSnapshot.ContentInfo(contentId, contentTitle, contentRegionId),
            new ReservationReadSnapshot.ParticipantInfo(
                participantUserId,
                participantName,
                participantPhone
            )
        );
    }

    public ReservationReadSnapshot.VisitInfo toVisitInfo() {
        if (visitId == null) {
            return null;
        }
        return new ReservationReadSnapshot.VisitInfo(
            visitId,
            visitReservationId,
            visitRegionId,
            visitSessionId,
            visitContentId,
            visitParticipantUserId,
            checkedAt
        );
    }
}
