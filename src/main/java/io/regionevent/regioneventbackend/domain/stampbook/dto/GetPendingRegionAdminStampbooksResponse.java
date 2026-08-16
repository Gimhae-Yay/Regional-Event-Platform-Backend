package io.regionevent.regioneventbackend.domain.stampbook.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.PendingRegionAdminStampbookResult;

public record GetPendingRegionAdminStampbooksResponse(
    List<StampbookResponse> stampbooks
) {

    public GetPendingRegionAdminStampbooksResponse {
        stampbooks = List.copyOf(stampbooks);
    }

    public static GetPendingRegionAdminStampbooksResponse from(
        List<PendingRegionAdminStampbookResult> results
    ) {
        return new GetPendingRegionAdminStampbooksResponse(results.stream()
            .map(StampbookResponse::from)
            .toList());
    }

    public record StampbookResponse(
        String stampbookId,
        String regionId,
        StampbookStatus status,
        int targetCount,
        String rewardCouponPolicyId,
        Instant requestedAt
    ) {

        private static StampbookResponse from(PendingRegionAdminStampbookResult result) {
            return new StampbookResponse(
                result.stampbookId().toString(),
                result.regionId().toString(),
                result.status(),
                result.targetCount(),
                result.rewardCouponPolicyId().toString(),
                result.requestedAt()
            );
        }
    }
}
