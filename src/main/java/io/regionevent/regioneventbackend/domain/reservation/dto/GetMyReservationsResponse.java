package io.regionevent.regioneventbackend.domain.reservation.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.service.GetMyReservationsUseCase.MyReservationListResult;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationReadResult;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationReadSnapshot;
import io.regionevent.regioneventbackend.domain.review.entity.ReviewStatus;
import io.regionevent.regioneventbackend.domain.review.service.ReviewService.ReviewReadResult;

public record GetMyReservationsResponse(
    List<ReservationResponse> reservations
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public static GetMyReservationsResponse from(List<MyReservationListResult> results) {
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
        CheckInResponse checkIn,
        @JsonInclude(JsonInclude.Include.ALWAYS) ReviewResponse review
    ) {

        private static ReservationResponse from(MyReservationListResult result) {
            ReservationReadResult reservation = result.reservation();
            ReservationReadSnapshot snapshot = reservation.snapshot();
            return new ReservationResponse(
                snapshot.reservation().reservationId().toString(),
                snapshot.reservation().reservationNo(),
                snapshot.reservation().status(),
                snapshot.reservation().quantity(),
                snapshot.reservation().confirmedAt(),
                ContentResponse.from(snapshot.content()),
                SessionResponse.from(snapshot.session()),
                new CheckInResponse(
                    reservation.checkIn().checkedIn(),
                    reservation.checkIn().checkedAt(),
                    reservation.checkIn().visitId() == null ? null : reservation.checkIn().visitId().toString()
                ),
                result.review() == null ? null : ReviewResponse.from(result.review())
            );
        }
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
        @JsonInclude(JsonInclude.Include.ALWAYS) String visitId
    ) {
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
