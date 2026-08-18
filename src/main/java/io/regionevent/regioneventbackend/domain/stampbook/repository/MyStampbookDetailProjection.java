package io.regionevent.regioneventbackend.domain.stampbook.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgressStatus;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

public record MyStampbookDetailProjection(
    Long stampbookId,
    String title,
    Long regionId,
    StampbookStatus stampbookStatus,
    Instant publishedAt,
    Instant endedAt,
    Long stampbookProgressId,
    Long progressUserId,
    StampbookProgressStatus progressStatus,
    Instant completedAt,
    Long contentId,
    String contentTitle,
    Instant earnedAt,
    Long stampbookRewardGrantId,
    Long completionRewardCouponPolicyId,
    Long stampbookRewardCouponPolicyId
) {
}
