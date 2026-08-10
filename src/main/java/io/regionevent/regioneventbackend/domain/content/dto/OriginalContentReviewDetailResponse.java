package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.service.OriginalContentReviewDetailResult;

public record OriginalContentReviewDetailResponse(
    String contentId,
    String regionId,
    String operatorId,
    String contentType,
    String status,
    String title,
    String description,
    String representativeImageUrl,
    Instant representativeImageUrlExpiresAt,
    String locationText,
    String operatingHoursText,
    String contactText,
    String precautions,
    String ageRequirement,
    String materials,
    String cancellationPolicyText,
    long reservationPrice,
    OffsetDateTime publishAt,
    List<Session> sessions
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public OriginalContentReviewDetailResponse {
        sessions = List.copyOf(sessions);
    }

    public static OriginalContentReviewDetailResponse from(OriginalContentReviewDetailResult result) {
        return new OriginalContentReviewDetailResponse(
            result.contentId().toString(),
            result.regionId().toString(),
            result.operatorId().toString(),
            result.contentType().name(),
            result.status().name(),
            result.title(),
            result.description(),
            result.representativeImageUrl(),
            result.representativeImageUrlExpiresAt(),
            result.locationText(),
            result.operatingHoursText(),
            result.contactText(),
            result.precautions(),
            result.ageRequirement(),
            result.materials(),
            result.cancellationPolicyText(),
            result.reservationPrice(),
            toSeoulOffsetDateTime(result.publishAt()),
            result.sessions().stream()
                .map(Session::from)
                .toList()
        );
    }

    private static OffsetDateTime toSeoulOffsetDateTime(Instant instant) {
        return instant.atZone(SEOUL_TIME_ZONE).toOffsetDateTime();
    }

    public record Session(
        String sessionId,
        String status,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime checkinOpenAt,
        OffsetDateTime checkinCloseAt,
        int capacity,
        int remainingCapacity
    ) {

        private static Session from(OriginalContentReviewDetailResult.Session session) {
            return new Session(
                session.sessionId().toString(),
                session.status().name(),
                toSeoulOffsetDateTime(session.startsAt()),
                toSeoulOffsetDateTime(session.endsAt()),
                toSeoulOffsetDateTime(session.checkinOpenAt()),
                toSeoulOffsetDateTime(session.checkinCloseAt()),
                session.capacity(),
                session.remainingCapacity()
            );
        }
    }
}
