package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuance;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;

public record GetMyCouponResult(
    Long couponId,
    Long couponPolicyId,
    String policyName,
    CouponIssuanceType issueSourceType,
    Long sourceId,
    CouponStatus status,
    long discountAmount,
    long minimumPaymentAmount,
    Instant issuedAt,
    Instant expiresAt,
    Long contentId,
    Long regionId,
    CouponPolicyStatus policyStatus,
    int validDaysAfterIssue
) {

    static GetMyCouponResult from(CouponIssuance couponIssuance) {
        CouponPolicy couponPolicy = couponIssuance.getCouponPolicy();
        return new GetMyCouponResult(
            couponIssuance.getCoupon().getCouponId(),
            couponPolicy.getCouponPolicyId(),
            couponPolicy.getName(),
            couponPolicy.getIssuanceType(),
            sourceId(couponIssuance),
            couponIssuance.getCoupon().getStatus(),
            couponPolicy.getDiscountAmount(),
            couponPolicy.getMinimumPaymentAmount(),
            couponIssuance.getCoupon().getIssuedAt(),
            couponIssuance.getCoupon().getExpiresAt(),
            couponPolicy.getContent().getContentId(),
            couponPolicy.getRegion().getRegionId(),
            couponPolicy.getStatus(),
            couponPolicy.getValidDays()
        );
    }

    private static Long sourceId(CouponIssuance couponIssuance) {
        if (couponIssuance.getVisit() != null) {
            return couponIssuance.getVisit().getVisitId();
        }
        if (couponIssuance.getMissionRewardClaim() != null) {
            return couponIssuance.getMissionRewardClaim().getMissionRewardClaimId();
        }
        if (couponIssuance.getStampbookRewardGrant() != null) {
            return couponIssuance.getStampbookRewardGrant().getStampbookRewardGrantId();
        }
        throw new IllegalStateException("coupon issuance source does not exist");
    }
}
