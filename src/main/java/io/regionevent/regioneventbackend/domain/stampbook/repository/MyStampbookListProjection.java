package io.regionevent.regioneventbackend.domain.stampbook.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgressStatus;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

public record MyStampbookListProjection(
    Long stampbookId,
    String title,
    Long regionId,
    StampbookStatus stampbookStatus,
    Instant publishedAt,
    Long stampbookProgressId,
    Long progressUserId,
    StampbookProgressStatus progressStatus,
    Instant completedAt,
    Long earnedCount,
    Long targetCount,
    Instant lastEarnedAt,
    Long stampbookRewardGrantId,
    Long completionRewardCouponPolicyId,
    Long stampbookRewardCouponPolicyId
) {
}
