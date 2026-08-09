package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;

public record CreateCouponPolicyResult(
    Long couponPolicyId,
    Long contentId,
    Long regionId,
    String name,
    CouponPolicyStatus status,
    CouponIssuanceType issueSourceType,
    long discountAmount,
    long minimumPaymentAmount,
    int validDaysAfterIssue,
    Instant issueStartsAt,
    Instant issueEndsAt,
    Long totalIssueLimit,
    Instant createdAt
) {

    public static CreateCouponPolicyResult from(CouponPolicy couponPolicy, Instant createdAt) {
        return new CreateCouponPolicyResult(
            couponPolicy.getCouponPolicyId(),
            couponPolicy.getContent().getContentId(),
            couponPolicy.getRegion().getRegionId(),
            couponPolicy.getName(),
            couponPolicy.getStatus(),
            couponPolicy.getIssuanceType(),
            couponPolicy.getDiscountAmount(),
            couponPolicy.getMinimumPaymentAmount(),
            couponPolicy.getValidDays(),
            couponPolicy.getIssueStartsAt(),
            couponPolicy.getIssueEndsAt(),
            couponPolicy.getTotalIssueLimit(),
            createdAt
        );
    }
}
