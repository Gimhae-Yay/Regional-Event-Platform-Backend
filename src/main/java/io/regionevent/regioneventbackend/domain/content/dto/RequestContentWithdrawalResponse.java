package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.content.service.RequestContentWithdrawalResult;

public record RequestContentWithdrawalResponse(
    String withdrawalRequestId,
    String contentId,
    ContentWithdrawalRequestStatus status,
    String requestReason,
    Instant requestedAt
) {

    public static RequestContentWithdrawalResponse from(RequestContentWithdrawalResult result) {
        return new RequestContentWithdrawalResponse(
            result.withdrawalRequestId().toString(),
            result.contentId().toString(),
            result.status(),
            result.requestReason(),
            result.requestedAt()
        );
    }
}
