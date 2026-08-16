package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

public record PendingRegionAdminStampbookResult(
    Long stampbookId,
    Long regionId,
    StampbookStatus status,
    int targetCount,
    Long rewardCouponPolicyId,
    Instant requestedAt
) {
}
