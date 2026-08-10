package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemption;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemptionStatus;

public record GetMyCouponUsageHistoryResult(
    Long couponId,
    List<UsageHistory> usageHistory
) {

    static GetMyCouponUsageHistoryResult from(
        Long couponId,
        List<CouponRedemption> couponRedemptions
    ) {
        return new GetMyCouponUsageHistoryResult(
            couponId,
            couponRedemptions.stream()
                .map(UsageHistory::from)
                .toList()
        );
    }

    public record UsageHistory(
        Long couponRedemptionId,
        Long reservationId,
        Long priceSnapshotId,
        CouponRedemptionStatus status,
        long discountAmount,
        Instant confirmedAt,
        Instant reversedAt,
        String reversalReason
    ) {

        private static UsageHistory from(CouponRedemption couponRedemption) {
            return new UsageHistory(
                couponRedemption.getCouponRedemptionId(),
                couponRedemption.getReservation().getReservationId(),
                couponRedemption.getReservationPriceSnapshot().getReservationPriceSnapshotId(),
                couponRedemption.getStatus(),
                couponRedemption.getReservationPriceSnapshot().getDiscountAmount(),
                couponRedemption.getRedeemedAt(),
                couponRedemption.getReversedAt(),
                null
            );
        }
    }
}
