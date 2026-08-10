package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Instant;
import java.util.List;

public record GetMyAvailableCouponsResult(
    Long holdId,
    Instant evaluatedAt,
    List<AvailableCoupon> availableCoupons
) {

    public record AvailableCoupon(
        CouponSummary coupon,
        long baseAmount,
        long discountAmount,
        long payableAmount
    ) {
    }
}
