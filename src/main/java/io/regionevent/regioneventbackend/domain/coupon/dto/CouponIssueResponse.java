package io.regionevent.regioneventbackend.domain.coupon.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponIssueResult;

public record CouponIssueResponse(
    String couponId,
    String couponPolicyId,
    String contentId,
    String regionId,
    String policyName,
    CouponIssuanceType issueSourceType,
    CouponStatus status,
    long discountAmount,
    long minimumPaymentAmount,
    Instant issuedAt,
    Instant expiresAt,
    boolean duplicate
) {

    public static CouponIssueResponse from(CouponIssueResult result) {
        return new CouponIssueResponse(
            result.couponId().toString(),
            result.couponPolicyId().toString(),
            result.contentId().toString(),
            result.regionId().toString(),
            result.policyName(),
            result.issueSourceType(),
            result.status(),
            result.discountAmount(),
            result.minimumPaymentAmount(),
            result.issuedAt(),
            result.expiresAt(),
            result.duplicate()
        );
    }
}
