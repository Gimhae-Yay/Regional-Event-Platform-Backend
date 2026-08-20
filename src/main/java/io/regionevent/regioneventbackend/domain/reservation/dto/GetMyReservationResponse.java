package io.regionevent.regioneventbackend.domain.reservation.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.service.GetMyReservationUseCase.MyReservationDetailResult;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationReadResult;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationReadSnapshot;
import io.regionevent.regioneventbackend.domain.review.entity.ReviewStatus;
import io.regionevent.regioneventbackend.domain.review.service.ReviewService.ReviewReadResult;

public record GetMyReservationResponse(
    ReservationResponse reservation,
    SessionResponse session,
    ContentResponse content,
    CheckInResponse checkIn,
    @JsonInclude(JsonInclude.Include.ALWAYS) ReviewResponse review
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public static GetMyReservationResponse from(MyReservationDetailResult result) {
        ReservationReadResult reservation = result.reservation();
        ReservationReadSnapshot snapshot = reservation.snapshot();
        return new GetMyReservationResponse(
            ReservationResponse.from(snapshot.reservation()),
            SessionResponse.from(snapshot.session(), snapshot.content()),
            ContentResponse.from(snapshot.content()),
            new CheckInResponse(
                reservation.checkIn().checkedIn(),
                reservation.checkIn().checkedAt(),
                reservation.checkIn().visitId() == null ? null : reservation.checkIn().visitId().toString()
            ),
            result.review() == null ? null : ReviewResponse.from(result.review())
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

    public record ContentResponse(
        String contentId,
        String title,
        String locationText
    ) {

        private static ContentResponse from(ReservationReadSnapshot.ContentInfo content) {
            return new ContentResponse(content.contentId().toString(), content.title(), content.locationText());
        }
    }

    public record ReviewResponse(
        String reviewId,
        ReviewStatus status,
        @JsonInclude(JsonInclude.Include.ALWAYS) Integer rating,
        @JsonInclude(JsonInclude.Include.ALWAYS) String reviewText,
        Instant createdAt,
        Instant updatedAt
    ) {

        private static ReviewResponse from(ReviewReadResult review) {
            return new ReviewResponse(
                review.reviewId().toString(),
                review.status(),
                review.rating(),
                review.reviewText(),
                review.createdAt(),
                review.updatedAt()
            );
        }
    }

    private static OffsetDateTime toSeoulOffsetDateTime(Instant instant) {
        return instant.atZone(SEOUL_TIME_ZONE).toOffsetDateTime();
    }
}
