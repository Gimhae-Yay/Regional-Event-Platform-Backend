package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.service.PendingContentWithdrawalRequestListResult;

public record PendingContentWithdrawalRequestsResponse(
    List<WithdrawalRequest> withdrawalRequests
) {

    public PendingContentWithdrawalRequestsResponse {
        withdrawalRequests = List.copyOf(withdrawalRequests);
    }

    public static PendingContentWithdrawalRequestsResponse from(
        PendingContentWithdrawalRequestListResult result
    ) {
        return new PendingContentWithdrawalRequestsResponse(
            result.withdrawalRequests().stream()
                .map(WithdrawalRequest::from)
                .toList()
        );
    }

    public record WithdrawalRequest(
        String withdrawalRequestId,
        String contentId,
        String contentType,
        String contentTitle,
        String contentStatus,
        Requester requester,
        Instant requestedAt
    ) {

        private static WithdrawalRequest from(
            PendingContentWithdrawalRequestListResult.WithdrawalRequest request
        ) {
            return new WithdrawalRequest(
                request.withdrawalRequestId().toString(),
                request.contentId().toString(),
                request.contentType().name(),
                request.contentTitle(),
                request.contentStatus().name(),
                Requester.from(request.requester()),
                request.requestedAt()
            );
        }
    }

    public record Requester(
        String userId,
        String name
    ) {

        private static Requester from(
            PendingContentWithdrawalRequestListResult.Requester requester
        ) {
            if (requester == null) {
                return null;
            }
            return new Requester(requester.userId().toString(), requester.name());
        }
    }
}
