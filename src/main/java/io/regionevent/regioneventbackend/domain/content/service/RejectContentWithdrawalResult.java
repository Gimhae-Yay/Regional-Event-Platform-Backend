package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;

public record RejectContentWithdrawalResult(
    Long withdrawalRequestId,
    Long contentId,
    ContentWithdrawalRequestStatus status,
    String rejectionReason,
    Instant rejectedAt
) {

    public static RejectContentWithdrawalResult from(ContentWithdrawalRequest request) {
        return new RejectContentWithdrawalResult(
            request.getContentWithdrawalRequestId(),
            request.getContent().getContentId(),
            request.getStatus(),
            request.getRejectionReason(),
            request.getReviewedAt()
        );
    }
}
