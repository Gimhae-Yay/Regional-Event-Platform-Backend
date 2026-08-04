package io.regionevent.regioneventbackend.domain.reservation.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;

public record QrExceptionDetailResult(
    Long exceptionId,
    QrExceptionType exceptionType,
    AuditEventResult result,
    String reasonCode,
    Instant occurredAt,
    boolean reservationResolved,
    ReservationInfo reservation
) {

    public static QrExceptionDetailResult unresolved(
        Long exceptionId,
        QrExceptionType exceptionType,
        AuditEventResult result,
        String reasonCode,
        Instant occurredAt
    ) {
        return new QrExceptionDetailResult(
            exceptionId,
            exceptionType,
            result,
            reasonCode,
            occurredAt,
            false,
            null
        );
    }

    public record ReservationInfo(
        ReservationReadSnapshot.ReservationInfo reservation,
        ReservationReadSnapshot.SessionInfo session,
        ReservationReadSnapshot.ContentInfo content,
        boolean memberLinked,
        ReservationParticipantMasker.MaskedParticipant participant,
        ReservationReadIntegrityValidator.CheckInInfo checkIn
    ) {
    }
}
