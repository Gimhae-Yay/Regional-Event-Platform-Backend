package io.regionevent.regioneventbackend.domain.coupon.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponSummary;

public record CouponSummaryResponse(
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
    Instant expiresAt
) {

    public static CouponSummaryResponse from(CouponSummary summary) {
        return new CouponSummaryResponse(
            summary.couponId().toString(),
            summary.couponPolicyId().toString(),
            summary.contentId().toString(),
            summary.regionId().toString(),
            summary.policyName(),
            summary.issueSourceType(),
            summary.status(),
            summary.discountAmount(),
            summary.minimumPaymentAmount(),
            summary.issuedAt(),
            summary.expiresAt()
        );
    }
}
