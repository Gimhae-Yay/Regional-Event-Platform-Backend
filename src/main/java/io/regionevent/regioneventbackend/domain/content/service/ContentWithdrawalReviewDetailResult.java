package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;

public record ContentWithdrawalReviewDetailResult(
    Long withdrawalRequestId,
    ContentWithdrawalRequestStatus status,
    Content content,
    Requester requester,
    String requestReason,
    Instant requestedAt
) {

    public record Content(
        Long contentId,
        ContentType contentType,
        String title,
        ContentStatus status,
        Instant publishAt
    ) {
    }

    public record Requester(
        Long userId,
        String name
    ) {
    }
}
