package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;

public record RequestContentWithdrawalResult(
    Long withdrawalRequestId,
    Long contentId,
    ContentWithdrawalRequestStatus status,
    String requestReason,
    Instant requestedAt
) {

    public static RequestContentWithdrawalResult from(ContentWithdrawalRequest request) {
        return new RequestContentWithdrawalResult(
            request.getContentWithdrawalRequestId(),
            request.getContent().getContentId(),
            ContentWithdrawalRequestStatus.PENDING,
            request.getRequestReason(),
            request.getRequestedAt()
        );
    }
}
