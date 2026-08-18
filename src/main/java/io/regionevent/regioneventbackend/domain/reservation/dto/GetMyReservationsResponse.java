package io.regionevent.regioneventbackend.domain.reservation.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationReadResult;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationReadSnapshot;

public record GetMyReservationsResponse(
    List<ReservationResponse> reservations
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public static GetMyReservationsResponse from(List<ReservationReadResult> results) {
        return new GetMyReservationsResponse(
            results.stream()
                .map(ReservationResponse::from)
                .toList()
        );
    }

    public record ReservationResponse(
        String reservationId,
        String reservationNo,
        ReservationStatus status,
        int quantity,
        Instant confirmedAt,
        ContentResponse content,
        SessionResponse session,
        CheckInResponse checkIn
    ) {

        private static ReservationResponse from(ReservationReadResult result) {
            ReservationReadSnapshot snapshot = result.snapshot();
            return new ReservationResponse(
                snapshot.reservation().reservationId().toString(),
                snapshot.reservation().reservationNo(),
                snapshot.reservation().status(),
                snapshot.reservation().quantity(),
                snapshot.reservation().confirmedAt(),
                ContentResponse.from(snapshot.content()),
                SessionResponse.from(snapshot.session()),
                new CheckInResponse(
                    result.checkIn().checkedIn(),
                    result.checkIn().checkedAt(),
                    result.checkIn().visitId() == null ? null : result.checkIn().visitId().toString()
                )
            );
        }
    }

    public record ContentResponse(
        String contentId,
        String title
    ) {

        private static ContentResponse from(ReservationReadSnapshot.ContentInfo content) {
            return new ContentResponse(content.contentId().toString(), content.title());
        }
    }

    public record SessionResponse(
        String sessionId,
        ContentSessionStatus status,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt
    ) {

        private static SessionResponse from(ReservationReadSnapshot.SessionInfo session) {
            return new SessionResponse(
                session.sessionId().toString(),
                session.status(),
                toSeoulOffsetDateTime(session.startsAt()),
                toSeoulOffsetDateTime(session.endsAt())
            );
        }
    }

    public record CheckInResponse(
        boolean checkedIn,
        Instant checkedAt,
        String visitId
    ) {
    }

    private static OffsetDateTime toSeoulOffsetDateTime(Instant instant) {
        return instant.atZone(SEOUL_TIME_ZONE).toOffsetDateTime();
    }
}
