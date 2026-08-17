package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

record StampbookReviewDetail(
    Long stampbookId,
    Long regionId,
    StampbookStatus status,
    List<TargetContent> targetContents,
    RewardCouponPolicy rewardCouponPolicy
) {

    record TargetContent(
        Long contentId,
        Long regionId,
        String title,
        ContentStatus status
    ) {
    }

    record RewardCouponPolicy(
        Long couponPolicyId,
        Long regionId,
        CouponIssuanceType issuanceType,
        CouponPolicyStatus status
    ) {
    }
}
