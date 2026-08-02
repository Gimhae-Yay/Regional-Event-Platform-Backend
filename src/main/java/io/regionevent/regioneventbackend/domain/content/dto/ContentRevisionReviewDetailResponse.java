package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.service.ContentRevisionReviewDetailResult;

public record ContentRevisionReviewDetailResponse(
    String revisionId,
    String contentId,
    String reviewType,
    String contentStatus,
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
    OffsetDateTime candidatePublishAt,
    List<Session> sessions,
    Instant submittedAt
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public ContentRevisionReviewDetailResponse {
        sessions = List.copyOf(sessions);
    }

    public static ContentRevisionReviewDetailResponse from(ContentRevisionReviewDetailResult result) {
        return new ContentRevisionReviewDetailResponse(
            result.revisionId().toString(),
            result.contentId().toString(),
            result.reviewType().name(),
            result.contentStatus().name(),
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
            toSeoulOffsetDateTime(result.candidatePublishAt()),
            result.sessions().stream()
                .map(Session::from)
                .toList(),
            result.submittedAt()
        );
    }

    private static OffsetDateTime toSeoulOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atZone(SEOUL_TIME_ZONE).toOffsetDateTime();
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

        private static Session from(ContentRevisionReviewDetailResult.Session session) {
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
