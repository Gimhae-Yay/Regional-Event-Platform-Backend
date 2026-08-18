package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;

public record PendingContentWithdrawalRequestListResult(
    List<WithdrawalRequest> withdrawalRequests
) {

    public PendingContentWithdrawalRequestListResult {
        withdrawalRequests = List.copyOf(withdrawalRequests);
    }

    public record WithdrawalRequest(
        Long withdrawalRequestId,
        Long contentId,
        ContentType contentType,
        String contentTitle,
        ContentStatus contentStatus,
        Requester requester,
        Instant requestedAt
    ) {
    }

    public record Requester(
        Long userId,
        String name
    ) {
    }
}
