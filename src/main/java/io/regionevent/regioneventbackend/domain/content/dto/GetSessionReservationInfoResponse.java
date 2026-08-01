package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import io.regionevent.regioneventbackend.domain.content.service.PublicSessionReservationInfo;

public record GetSessionReservationInfoResponse(
    String sessionId,
    String contentId,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    int price,
    int remainingCapacity,
    boolean reservable
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");
    private static final int FREE_RESERVATION_PRICE = 0;

    public static GetSessionReservationInfoResponse from(PublicSessionReservationInfo reservationInfo) {
        return new GetSessionReservationInfoResponse(
            reservationInfo.sessionId().toString(),
            reservationInfo.contentId().toString(),
            reservationInfo.startsAt().atZone(SEOUL_TIME_ZONE).toOffsetDateTime(),
            reservationInfo.endsAt().atZone(SEOUL_TIME_ZONE).toOffsetDateTime(),
            FREE_RESERVATION_PRICE,
            reservationInfo.remainingCapacity(),
            reservationInfo.isReservable()
        );
    }
}
