package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentWithdrawalResult;

public record RejectContentWithdrawalResponse(
    String withdrawalRequestId,
    String contentId,
    ContentWithdrawalRequestStatus status,
    String rejectionReason,
    Instant rejectedAt
) {

    public static RejectContentWithdrawalResponse from(RejectContentWithdrawalResult result) {
        return new RejectContentWithdrawalResponse(
            result.withdrawalRequestId().toString(),
            result.contentId().toString(),
            result.status(),
            result.rejectionReason(),
            result.rejectedAt()
        );
    }
}
