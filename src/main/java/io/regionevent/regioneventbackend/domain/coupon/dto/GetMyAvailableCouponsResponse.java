package io.regionevent.regioneventbackend.domain.coupon.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.GetMyAvailableCouponsResult;

public record GetMyAvailableCouponsResponse(
    String holdId,
    Instant evaluatedAt,
    List<AvailableCouponResponse> availableCoupons
) {

    public static GetMyAvailableCouponsResponse from(GetMyAvailableCouponsResult result) {
        return new GetMyAvailableCouponsResponse(
            result.holdId().toString(),
            result.evaluatedAt(),
            result.availableCoupons().stream()
                .map(AvailableCouponResponse::from)
                .toList()
        );
    }

    public record AvailableCouponResponse(
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
        DiscountPreviewResponse discountPreview
    ) {

        private static AvailableCouponResponse from(GetMyAvailableCouponsResult.AvailableCoupon coupon) {
            return new AvailableCouponResponse(
                coupon.coupon().couponId().toString(),
                coupon.coupon().couponPolicyId().toString(),
                coupon.coupon().contentId().toString(),
                coupon.coupon().regionId().toString(),
                coupon.coupon().policyName(),
                coupon.coupon().issueSourceType(),
                coupon.coupon().status(),
                coupon.coupon().discountAmount(),
                coupon.coupon().minimumPaymentAmount(),
                coupon.coupon().issuedAt(),
                coupon.coupon().expiresAt(),
                new DiscountPreviewResponse(coupon.baseAmount(), coupon.discountAmount(), coupon.payableAmount())
            );
        }
    }

    public record DiscountPreviewResponse(
        long baseAmount,
        long discountAmount,
        long payableAmount
    ) {
    }
}
