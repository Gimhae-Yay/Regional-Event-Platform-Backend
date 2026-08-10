package io.regionevent.regioneventbackend.domain.coupon.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.GetMyCouponResult;

public record GetMyCouponResponse(
    CouponDetail coupon,
    PolicyDetail policy
) {

    public static GetMyCouponResponse from(GetMyCouponResult result) {
        return new GetMyCouponResponse(
            new CouponDetail(
                result.couponId().toString(),
                result.couponPolicyId().toString(),
                result.policyName(),
                result.issueSourceType(),
                result.sourceId().toString(),
                result.status(),
                result.discountAmount(),
                result.minimumPaymentAmount(),
                result.issuedAt(),
                result.expiresAt()
            ),
            new PolicyDetail(
                result.contentId().toString(),
                result.regionId().toString(),
                result.policyStatus(),
                result.validDaysAfterIssue()
            )
        );
    }

    public record CouponDetail(
        String couponId,
        String couponPolicyId,
        String policyName,
        CouponIssuanceType issueSourceType,
        String sourceId,
        CouponStatus status,
        long discountAmount,
        long minimumPaymentAmount,
        Instant issuedAt,
        Instant expiresAt
    ) {
    }

    public record PolicyDetail(
        String contentId,
        String regionId,
        CouponPolicyStatus status,
        int validDaysAfterIssue
    ) {
    }
}
