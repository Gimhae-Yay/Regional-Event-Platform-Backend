package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;

public record UpdateCouponPolicyResult(
    Long couponPolicyId,
    CouponPolicyStatus status,
    String name,
    long discountAmount,
    long minimumPaymentAmount,
    int validDaysAfterIssue,
    Instant updatedAt
) {

    public static UpdateCouponPolicyResult from(
        CouponPolicy couponPolicy,
        Instant updatedAt
    ) {
        return new UpdateCouponPolicyResult(
            couponPolicy.getCouponPolicyId(),
            couponPolicy.getStatus(),
            couponPolicy.getName(),
            couponPolicy.getDiscountAmount(),
            couponPolicy.getMinimumPaymentAmount(),
            couponPolicy.getValidDays(),
            updatedAt
        );
    }
}
