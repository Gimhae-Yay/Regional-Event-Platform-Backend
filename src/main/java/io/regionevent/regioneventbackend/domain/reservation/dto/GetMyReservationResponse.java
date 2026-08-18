package io.regionevent.regioneventbackend.domain.reservation.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationReadResult;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationReadSnapshot;

public record GetMyReservationResponse(
    ReservationResponse reservation,
    SessionResponse session,
    CheckInResponse checkIn
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public static GetMyReservationResponse from(ReservationReadResult result) {
        ReservationReadSnapshot snapshot = result.snapshot();
        return new GetMyReservationResponse(
            ReservationResponse.from(snapshot.reservation()),
            SessionResponse.from(snapshot.session(), snapshot.content()),
            new CheckInResponse(
                result.checkIn().checkedIn(),
                result.checkIn().checkedAt(),
                result.checkIn().visitId() == null ? null : result.checkIn().visitId().toString()
            )
        );
    }

    public record ReservationResponse(
        String reservationId,
        String reservationNo,
        ReservationStatus status,
        int quantity,
        Instant confirmedAt,
        Instant cancelledAt,
        String cancellationReason,
        Instant expiredAt
    ) {

        private static ReservationResponse from(ReservationReadSnapshot.ReservationInfo reservation) {
            return new ReservationResponse(
                reservation.reservationId().toString(),
                reservation.reservationNo(),
                reservation.status(),
                reservation.quantity(),
                reservation.confirmedAt(),
                reservation.cancelledAt(),
                reservation.cancellationReason(),
                reservation.expiredAt()
            );
        }
    }

    public record SessionResponse(
        String sessionId,
        String contentId,
        ContentSessionStatus status,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime checkinOpenAt,
        OffsetDateTime checkinCloseAt
    ) {

        private static SessionResponse from(
            ReservationReadSnapshot.SessionInfo session,
            ReservationReadSnapshot.ContentInfo content
        ) {
            return new SessionResponse(
                session.sessionId().toString(),
                content.contentId().toString(),
                session.status(),
                toSeoulOffsetDateTime(session.startsAt()),
                toSeoulOffsetDateTime(session.endsAt()),
                toSeoulOffsetDateTime(session.checkinOpenAt()),
                toSeoulOffsetDateTime(session.checkinCloseAt())
            );
        }
    }

    public record CheckInResponse(
        boolean checkedIn,
        Instant checkedAt,
        @JsonInclude(JsonInclude.Include.ALWAYS) String visitId
    ) {
    }

    private static OffsetDateTime toSeoulOffsetDateTime(Instant instant) {
        return instant.atZone(SEOUL_TIME_ZONE).toOffsetDateTime();
    }
}
