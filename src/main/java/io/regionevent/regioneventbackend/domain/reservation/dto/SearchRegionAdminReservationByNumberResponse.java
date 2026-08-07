package io.regionevent.regioneventbackend.domain.reservation.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import io.regionevent.regioneventbackend.domain.reservation.service.RegionAdminReservationSearchResult;

public record SearchRegionAdminReservationByNumberResponse(
    String reservationId,
    String reservationNo,
    String status,
    ContentResponse content,
    SessionResponse session,
    ParticipantResponse participant,
    CheckInResponse checkIn
) {

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

    public static SearchRegionAdminReservationByNumberResponse from(
        RegionAdminReservationSearchResult result
    ) {
        return new SearchRegionAdminReservationByNumberResponse(
            result.reservation().reservationId().toString(),
            result.reservation().reservationNo(),
            result.reservation().status().name(),
            new ContentResponse(
                result.content().contentId().toString(),
                result.content().title()
            ),
            new SessionResponse(
                result.session().sessionId().toString(),
                result.session().status().name(),
                toSeoulOffsetDateTime(result.session().startsAt()),
                toSeoulOffsetDateTime(result.session().endsAt()),
                toSeoulOffsetDateTime(result.session().checkinOpenAt()),
                toSeoulOffsetDateTime(result.session().checkinCloseAt())
            ),
            new ParticipantResponse(
                result.participant().name(),
                result.participant().phone()
            ),
            new CheckInResponse(
                result.checkIn().checkedIn(),
                false,
                result.checkIn().checkedAt()
            )
        );
    }

    private static OffsetDateTime toSeoulOffsetDateTime(Instant value) {
        return value.atOffset(SEOUL_OFFSET);
    }

    public record ContentResponse(
        String contentId,
        String title
    ) {
    }

    public record SessionResponse(
        String sessionId,
        String status,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime checkinOpenAt,
        OffsetDateTime checkinCloseAt
    ) {
    }

    public record ParticipantResponse(
        String name,
        String phone
    ) {
    }

    public record CheckInResponse(
        boolean checkedIn,
        boolean canCheckIn,
        Instant checkedAt
    ) {
    }
}
