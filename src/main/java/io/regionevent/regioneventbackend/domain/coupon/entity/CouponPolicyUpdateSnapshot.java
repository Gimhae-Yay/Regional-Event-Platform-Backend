package io.regionevent.regioneventbackend.domain.coupon.entity;

import java.time.Instant;

public record CouponPolicyUpdateSnapshot(
    String name,
    String description,
    long discountAmount,
    long minimumPaymentAmount,
    int validDays,
    Instant issueStartsAt,
    Instant issueEndsAt,
    Long totalIssueLimit
) {

    public static CouponPolicyUpdateSnapshot from(CouponPolicy couponPolicy) {
        return new CouponPolicyUpdateSnapshot(
            couponPolicy.getName(),
            couponPolicy.getDescription(),
            couponPolicy.getDiscountAmount(),
            couponPolicy.getMinimumPaymentAmount(),
            couponPolicy.getValidDays(),
            couponPolicy.getIssueStartsAt(),
            couponPolicy.getIssueEndsAt(),
            couponPolicy.getTotalIssueLimit()
        );
    }
}
