package io.regionevent.regioneventbackend.domain.coupon.dto;

import java.util.List;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.OperatorCouponPolicySummary;

public record GetOperatorCouponPoliciesResponse(List<CouponPolicySummary> couponPolicies) {

    public static GetOperatorCouponPoliciesResponse from(List<OperatorCouponPolicySummary> summaries) {
        return new GetOperatorCouponPoliciesResponse(summaries.stream()
            .map(CouponPolicySummary::from)
            .toList());
    }

    public record CouponPolicySummary(
        String couponPolicyId,
        String contentId,
        String name,
        CouponPolicyStatus status
    ) {

        private static CouponPolicySummary from(OperatorCouponPolicySummary summary) {
            return new CouponPolicySummary(
                summary.couponPolicyId().toString(),
                summary.contentId().toString(),
                summary.name(),
                summary.status()
            );
        }
    }
}
