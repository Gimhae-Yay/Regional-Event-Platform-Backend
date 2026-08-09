package io.regionevent.regioneventbackend.domain.coupon.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.PublishCouponPolicyResult;

public record PublishCouponPolicyResponse(
    String couponPolicyId,
    CouponPolicyStatus status,
    Instant publishedAt
) {

    public static PublishCouponPolicyResponse from(PublishCouponPolicyResult result) {
        return new PublishCouponPolicyResponse(
            result.couponPolicyId().toString(),
            result.status(),
            result.publishedAt()
        );
    }
}
