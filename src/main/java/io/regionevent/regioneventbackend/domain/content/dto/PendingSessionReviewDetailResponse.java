package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import io.regionevent.regioneventbackend.domain.content.service.PendingSessionReviewDetailResult;

public record PendingSessionReviewDetailResponse(
    String sessionId, String contentId, String contentTitle, String contentStatus, String status,
    OffsetDateTime startsAt, OffsetDateTime endsAt, OffsetDateTime checkinOpenAt, OffsetDateTime checkinCloseAt,
    int capacity, int remainingCapacity, Instant createdAt, Operator operator
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public static PendingSessionReviewDetailResponse from(PendingSessionReviewDetailResult result) {
        return new PendingSessionReviewDetailResponse(result.sessionId().toString(), result.contentId().toString(),
            result.contentTitle(), result.contentStatus(), result.status(), toSeoulOffsetDateTime(result.startsAt()),
            toSeoulOffsetDateTime(result.endsAt()), toSeoulOffsetDateTime(result.checkinOpenAt()),
            toSeoulOffsetDateTime(result.checkinCloseAt()), result.capacity(), result.remainingCapacity(),
            result.createdAt(), new Operator(result.operatorId().toString(), result.operatorName()));
    }

    private static OffsetDateTime toSeoulOffsetDateTime(Instant instant) {
        return instant.atZone(SEOUL_TIME_ZONE).toOffsetDateTime();
    }

    public record Operator(String operatorId, String name) {
    }
}
