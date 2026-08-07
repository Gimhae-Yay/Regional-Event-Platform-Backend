package io.regionevent.regioneventbackend.domain.reservation.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.service.SessionReservationListResult;
import io.regionevent.regioneventbackend.domain.reservation.service.SessionReservationReadResult;

public record GetSessionReservationsResponse(
    String contentId,
    SessionResponse session,
    List<ReservationResponse> reservations
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public static GetSessionReservationsResponse from(SessionReservationListResult result) {
        return new GetSessionReservationsResponse(
            result.contentId().toString(),
            SessionResponse.from(result.session()),
            result.reservations().stream()
                .map(ReservationResponse::from)
                .toList()
        );
    }

    public record SessionResponse(
        String sessionId,
        ContentSessionStatus status,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime checkinOpenAt,
        OffsetDateTime checkinCloseAt
    ) {

        private static SessionResponse from(SessionReservationListResult.SessionInfo session) {
            return new SessionResponse(
                session.sessionId().toString(),
                session.status(),
                toSeoulOffsetDateTime(session.startsAt()),
                toSeoulOffsetDateTime(session.endsAt()),
                toSeoulOffsetDateTime(session.checkinOpenAt()),
                toSeoulOffsetDateTime(session.checkinCloseAt())
            );
        }
    }

    public record ReservationResponse(
        String reservationId,
        String reservationNo,
        ReservationStatus status,
        int quantity,
        Instant confirmedAt,
        ParticipantResponse participant,
        CheckInResponse checkIn
    ) {

        private static ReservationResponse from(SessionReservationReadResult reservation) {
            return new ReservationResponse(
                reservation.reservationId().toString(),
                reservation.reservationNo(),
                reservation.status(),
                reservation.quantity(),
                reservation.confirmedAt(),
                new ParticipantResponse(
                    reservation.participant().name(),
                    reservation.participant().phone()
                ),
                new CheckInResponse(
                    reservation.checkIn().checkedIn(),
                    reservation.checkIn().checkedAt()
                )
            );
        }
    }

    public record ParticipantResponse(
        String name,
        String phone
    ) {
    }

    public record CheckInResponse(
        boolean checkedIn,
        Instant checkedAt
    ) {
    }

    private static OffsetDateTime toSeoulOffsetDateTime(Instant instant) {
        return instant.atZone(SEOUL_TIME_ZONE).toOffsetDateTime();
    }
}
