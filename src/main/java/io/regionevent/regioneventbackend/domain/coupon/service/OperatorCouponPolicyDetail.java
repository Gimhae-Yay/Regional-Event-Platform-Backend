package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;

public record OperatorCouponPolicyDetail(
    Long couponPolicyId,
    Long contentId,
    Long regionId,
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

    static OperatorCouponPolicyDetail from(CouponPolicy couponPolicy) {
        return new OperatorCouponPolicyDetail(
            couponPolicy.getCouponPolicyId(),
            couponPolicy.getContent().getContentId(),
            couponPolicy.getRegion().getRegionId(),
            couponPolicy.getName(),
            couponPolicy.getDescription(),
            couponPolicy.getStatus(),
            couponPolicy.getIssuanceType(),
            couponPolicy.getDiscountAmount(),
            couponPolicy.getMinimumPaymentAmount(),
            couponPolicy.getValidDays(),
            couponPolicy.getIssueStartsAt(),
            couponPolicy.getIssueEndsAt(),
            couponPolicy.getTotalIssueLimit(),
            couponPolicy.getIssuedCount(),
            couponPolicy.getPublishedAt(),
            couponPolicy.getEndedAt()
        );
    }
}
