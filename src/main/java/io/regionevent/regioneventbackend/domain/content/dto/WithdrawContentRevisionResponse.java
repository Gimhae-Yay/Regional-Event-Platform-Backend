package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.service.WithdrawContentRevisionResult;

public record WithdrawContentRevisionResponse(
    String revisionId,
    String contentId,
    String status,
    String withdrawalReason,
    Instant withdrawnAt
) {

    public static WithdrawContentRevisionResponse from(WithdrawContentRevisionResult result) {
        return new WithdrawContentRevisionResponse(
            result.revisionId().toString(),
            result.contentId().toString(),
            result.status().name(),
            result.withdrawalReason(),
            result.withdrawnAt()
        );
    }
}
