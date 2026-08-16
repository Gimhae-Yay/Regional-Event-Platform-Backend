package io.regionevent.regioneventbackend.domain.coupon.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.OperatorCouponPolicyDetail;

public record GetOperatorCouponPolicyResponse(
    String couponPolicyId,
    String contentId,
    String regionId,
    String name,
    String description,
    CouponPolicyStatus status,
    CouponIssuanceType issueSourceType,
    long discountAmount,
    long minimumPaymentAmount,
    int validDaysAfterIssue,
    Instant issueStartsAt,
    Instant issueEndsAt,
    Long totalIssueLimit,
    long issuedCount,
    Instant publishedAt,
    Instant endedAt
) {

    public static GetOperatorCouponPolicyResponse from(OperatorCouponPolicyDetail detail) {
        return new GetOperatorCouponPolicyResponse(
            detail.couponPolicyId().toString(),
            detail.contentId().toString(),
            detail.regionId().toString(),
            detail.name(),
            detail.description(),
            detail.status(),
            detail.issueSourceType(),
            detail.discountAmount(),
            detail.minimumPaymentAmount(),
            detail.validDaysAfterIssue(),
            detail.issueStartsAt(),
            detail.issueEndsAt(),
            detail.totalIssueLimit(),
            detail.issuedCount(),
            detail.publishedAt(),
            detail.endedAt()
        );
    }
}
