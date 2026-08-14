package io.regionevent.regioneventbackend.domain.stampbook.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

public record PendingRegionAdminStampbookProjection(
    Long stampbookId,
    Long regionId,
    StampbookStatus status,
    Long targetCount,
    Long rewardCouponPolicyId,
    Instant requestedAt
) {
}
