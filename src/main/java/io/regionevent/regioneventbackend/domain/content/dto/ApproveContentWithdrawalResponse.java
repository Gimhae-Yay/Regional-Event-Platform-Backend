package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentWithdrawalResult;

public record ApproveContentWithdrawalResponse(
    String withdrawalRequestId,
    ContentWithdrawalRequestStatus requestStatus,
    String contentId,
    ContentStatus contentStatus,
    String withdrawalReason,
    Instant approvedAt
) {

    public static ApproveContentWithdrawalResponse from(ApproveContentWithdrawalResult result) {
        return new ApproveContentWithdrawalResponse(
            result.withdrawalRequestId().toString(),
            result.requestStatus(),
            result.contentId().toString(),
            result.contentStatus(),
            result.withdrawalReason(),
            result.approvedAt()
        );
    }
}
