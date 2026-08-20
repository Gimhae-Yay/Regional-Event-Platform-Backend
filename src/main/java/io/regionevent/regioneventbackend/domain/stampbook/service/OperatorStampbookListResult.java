package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

public record OperatorStampbookListResult(
    Long stampbookId,
    String title,
    Long regionId,
    StampbookStatus status,
    int targetCount,
    Long rewardCouponPolicyId,
    Instant publishedAt,
    Instant endedAt
) {
}
