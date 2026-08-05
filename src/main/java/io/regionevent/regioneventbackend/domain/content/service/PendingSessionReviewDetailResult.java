package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;

public record PendingSessionReviewDetailResult(
    Long sessionId, Long contentId, String contentTitle, String contentStatus, String status,
    Instant startsAt, Instant endsAt, Instant checkinOpenAt, Instant checkinCloseAt,
    int capacity, int remainingCapacity, Instant createdAt, Long operatorId, String operatorName
) {
    public static PendingSessionReviewDetailResult from(ContentSession session) {
        return new PendingSessionReviewDetailResult(session.getSessionId(), session.getContent().getContentId(),
            session.getContent().getTitle(), session.getContent().getStatus().name(), session.getStatus().name(),
            session.getStartsAt(), session.getEndsAt(), session.getCheckinOpenAt(), session.getCheckinCloseAt(),
            session.getCapacity(), session.getRemainingCapacity(), session.getCreatedAt(),
            session.getContent().getOperator().getUserId(), session.getContent().getOperator().getName());
    }
}
