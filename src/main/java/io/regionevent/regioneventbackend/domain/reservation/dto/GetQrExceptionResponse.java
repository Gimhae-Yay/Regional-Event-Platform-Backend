package io.regionevent.regioneventbackend.domain.reservation.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import io.regionevent.regioneventbackend.domain.reservation.service.QrExceptionDetailResult;

public record GetQrExceptionResponse(
    Long exceptionId,
    String exceptionType,
    String result,
    String reasonCode,
    OffsetDateTime occurredAt,
    boolean reservationResolved,
    ReservationResponse reservation
) {

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

    public static GetQrExceptionResponse from(QrExceptionDetailResult result) {
        return new GetQrExceptionResponse(
            result.exceptionId(),
            result.exceptionType().name(),
            result.result().name(),
            result.reasonCode(),
            toSeoulOffsetDateTime(result.occurredAt()),
            result.reservationResolved(),
            toReservationResponse(result.reservation())
        );
    }

    private static ReservationResponse toReservationResponse(
        QrExceptionDetailResult.ReservationInfo reservationInfo
    ) {
        if (reservationInfo == null) {
            return null;
        }
        return new ReservationResponse(
            reservationInfo.reservation().reservationId(),
            reservationInfo.reservation().reservationNo(),
            reservationInfo.reservation().status().name(),
            reservationInfo.content().contentId(),
            reservationInfo.content().title(),
            reservationInfo.session().sessionId(),
            toSeoulOffsetDateTime(reservationInfo.session().startsAt()),
            toSeoulOffsetDateTime(reservationInfo.session().checkinOpenAt()),
            toSeoulOffsetDateTime(reservationInfo.session().checkinCloseAt()),
            new ParticipantResponse(
                reservationInfo.memberLinked(),
                reservationInfo.participant().name(),
                reservationInfo.participant().phone()
            ),
            new CheckInResponse(
                reservationInfo.checkIn().checkedIn(),
                false,
                toSeoulOffsetDateTime(reservationInfo.checkIn().checkedAt())
            )
        );
    }

    private static OffsetDateTime toSeoulOffsetDateTime(Instant value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(SEOUL_OFFSET);
    }

    public record ReservationResponse(
        Long reservationId,
        String reservationNo,
        String status,
        Long contentId,
        String contentTitle,
        Long sessionId,
        OffsetDateTime startsAt,
        OffsetDateTime checkinOpenAt,
        OffsetDateTime checkinCloseAt,
        ParticipantResponse participant,
        CheckInResponse checkIn
    ) {
    }

    public record ParticipantResponse(
        boolean memberLinked,
        String name,
        String phone
    ) {
    }

    public record CheckInResponse(
        boolean checkedIn,
        boolean canCheckIn,
        OffsetDateTime checkedAt
    ) {
    }
}
