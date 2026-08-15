package io.regionevent.regioneventbackend.domain.coupon.service;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;

public record OperatorCouponPolicySummary(
    Long couponPolicyId,
    Long contentId,
    String name,
    CouponPolicyStatus status
) {

    static OperatorCouponPolicySummary from(CouponPolicy couponPolicy) {
        return new OperatorCouponPolicySummary(
            couponPolicy.getCouponPolicyId(),
            couponPolicy.getContent().getContentId(),
            couponPolicy.getName(),
            couponPolicy.getStatus()
        );
    }
}
