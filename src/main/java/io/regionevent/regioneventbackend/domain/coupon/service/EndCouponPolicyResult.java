package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;

public record EndCouponPolicyResult(
    Long couponPolicyId,
    CouponPolicyStatus status,
    Instant endedAt
) {

    public static EndCouponPolicyResult from(CouponPolicy couponPolicy) {
        return new EndCouponPolicyResult(
            couponPolicy.getCouponPolicyId(),
            couponPolicy.getStatus(),
            couponPolicy.getEndedAt()
        );
    }
}
