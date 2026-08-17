package io.regionevent.regioneventbackend.domain.coupon.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.EndCouponPolicyResult;

public record EndCouponPolicyResponse(
    String couponPolicyId,
    CouponPolicyStatus status,
    Instant endedAt
) {

    public static EndCouponPolicyResponse from(EndCouponPolicyResult result) {
        return new EndCouponPolicyResponse(
            result.couponPolicyId().toString(),
            result.status(),
            result.endedAt()
        );
    }
}
