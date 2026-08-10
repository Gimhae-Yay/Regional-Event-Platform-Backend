package io.regionevent.regioneventbackend.domain.coupon.dto;

import java.util.List;

import io.regionevent.regioneventbackend.domain.coupon.service.CouponSummary;

public record GetMyCouponsResponse(List<CouponSummaryResponse> coupons) {

    public static GetMyCouponsResponse from(List<CouponSummary> summaries) {
        return new GetMyCouponsResponse(summaries.stream()
            .map(CouponSummaryResponse::from)
            .toList());
    }
}
