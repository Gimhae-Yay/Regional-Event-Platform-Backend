package io.regionevent.regioneventbackend.domain.stampbook.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

public record OperatorStampbookListProjection(
    Long stampbookId,
    String title,
    Long regionId,
    StampbookStatus status,
    Long targetCount,
    Long minimumTargetContentRegionId,
    Long maximumTargetContentRegionId,
    Long rewardCouponPolicyId,
    Long rewardCouponPolicyRegionId,
    Instant publishedAt,
    Instant endedAt
) {
}
