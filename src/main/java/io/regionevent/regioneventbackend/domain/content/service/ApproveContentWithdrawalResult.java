package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;

public record ApproveContentWithdrawalResult(
    Long withdrawalRequestId,
    ContentWithdrawalRequestStatus requestStatus,
    Long contentId,
    ContentStatus contentStatus,
    String withdrawalReason,
    Instant approvedAt
) {

    public static ApproveContentWithdrawalResult from(
        ContentWithdrawalRequest request,
        Content content
    ) {
        return new ApproveContentWithdrawalResult(
            request.getContentWithdrawalRequestId(),
            request.getStatus(),
            content.getContentId(),
            content.getStatus(),
            request.getRequestReason(),
            request.getReviewedAt()
        );
    }
}
