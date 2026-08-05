package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.service.PendingSessionReviewDetailResult;

public record PendingSessionReviewDetailResponse(
    String sessionId, String contentId, String contentTitle, String contentStatus, String status,
    Instant startsAt, Instant endsAt, Instant checkinOpenAt, Instant checkinCloseAt,
    int capacity, int remainingCapacity, Instant createdAt, Operator operator
) {
    public static PendingSessionReviewDetailResponse from(PendingSessionReviewDetailResult result) {
        return new PendingSessionReviewDetailResponse(result.sessionId().toString(), result.contentId().toString(),
            result.contentTitle(), result.contentStatus(), result.status(), result.startsAt(), result.endsAt(),
            result.checkinOpenAt(), result.checkinCloseAt(), result.capacity(), result.remainingCapacity(),
            result.createdAt(), new Operator(result.operatorId().toString(), result.operatorName()));
    }

    public record Operator(String operatorId, String name) {
    }
}
