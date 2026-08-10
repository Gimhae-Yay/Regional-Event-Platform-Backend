package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;

public record CouponIssueResult(
    Long couponId,
    Long couponPolicyId,
    Long contentId,
    Long regionId,
    String policyName,
    CouponIssuanceType issueSourceType,
    CouponStatus status,
    long discountAmount,
    long minimumPaymentAmount,
    Instant issuedAt,
    Instant expiresAt,
    boolean duplicate
) {

    public static CouponIssueResult from(Coupon coupon, boolean duplicate) {
        return new CouponIssueResult(
            coupon.getCouponId(),
            coupon.getCouponPolicy().getCouponPolicyId(),
            coupon.getCouponPolicy().getContent().getContentId(),
            coupon.getCouponPolicy().getRegion().getRegionId(),
            coupon.getCouponPolicy().getName(),
            coupon.getCouponPolicy().getIssuanceType(),
            coupon.getStatus(),
            coupon.getCouponPolicy().getDiscountAmount(),
            coupon.getCouponPolicy().getMinimumPaymentAmount(),
            coupon.getIssuedAt(),
            coupon.getExpiresAt(),
            duplicate
        );
    }
}
