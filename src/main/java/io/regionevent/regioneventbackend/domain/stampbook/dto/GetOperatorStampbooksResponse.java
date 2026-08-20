package io.regionevent.regioneventbackend.domain.stampbook.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.OperatorStampbookListResult;

public record GetOperatorStampbooksResponse(List<StampbookResponse> stampbooks) {

    public static GetOperatorStampbooksResponse from(List<OperatorStampbookListResult> results) {
        return new GetOperatorStampbooksResponse(results.stream()
            .map(StampbookResponse::from)
            .toList());
    }

    public record StampbookResponse(
        String stampbookId,
        String title,
        String regionId,
        StampbookStatus status,
        int targetCount,
        String rewardCouponPolicyId,
        Instant publishedAt,
        Instant endedAt
    ) {

        private static StampbookResponse from(OperatorStampbookListResult result) {
            return new StampbookResponse(
                result.stampbookId().toString(),
                result.title(),
                result.regionId().toString(),
                result.status(),
                result.targetCount(),
                result.rewardCouponPolicyId().toString(),
                result.publishedAt(),
                result.endedAt()
            );
        }
    }
}
