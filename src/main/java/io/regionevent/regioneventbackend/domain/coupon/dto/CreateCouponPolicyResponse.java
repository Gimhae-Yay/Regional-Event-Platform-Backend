package io.regionevent.regioneventbackend.domain.coupon.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.CreateCouponPolicyResult;

public record CreateCouponPolicyResponse(
    String couponPolicyId,
    String contentId,
    String regionId,
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

    public static CreateCouponPolicyResponse from(CreateCouponPolicyResult result) {
        return new CreateCouponPolicyResponse(
            result.couponPolicyId().toString(),
            result.contentId().toString(),
            result.regionId().toString(),
            result.name(),
            result.status(),
            result.issueSourceType(),
            result.discountAmount(),
            result.minimumPaymentAmount(),
            result.validDaysAfterIssue(),
            result.issueStartsAt(),
            result.issueEndsAt(),
            result.totalIssueLimit(),
            result.createdAt()
        );
    }
}
