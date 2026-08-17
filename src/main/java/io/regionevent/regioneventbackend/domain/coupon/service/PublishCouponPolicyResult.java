package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;

public record PublishCouponPolicyResult(
    Long couponPolicyId,
    CouponPolicyStatus status,
    Instant publishedAt
) {

    public static PublishCouponPolicyResult from(CouponPolicy couponPolicy) {
        return new PublishCouponPolicyResult(
            couponPolicy.getCouponPolicyId(),
            couponPolicy.getStatus(),
            couponPolicy.getPublishedAt()
        );
    }
}
