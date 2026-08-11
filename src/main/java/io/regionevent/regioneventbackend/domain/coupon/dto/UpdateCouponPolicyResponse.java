package io.regionevent.regioneventbackend.domain.coupon.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.UpdateCouponPolicyResult;

public record UpdateCouponPolicyResponse(
    String couponPolicyId,
    CouponPolicyStatus status,
    String name,
    long discountAmount,
    long minimumPaymentAmount,
    int validDaysAfterIssue,
    Instant updatedAt
) {

    public static UpdateCouponPolicyResponse from(UpdateCouponPolicyResult result) {
        return new UpdateCouponPolicyResponse(
            result.couponPolicyId().toString(),
            result.status(),
            result.name(),
            result.discountAmount(),
            result.minimumPaymentAmount(),
            result.validDaysAfterIssue(),
            result.updatedAt()
        );
    }
}
