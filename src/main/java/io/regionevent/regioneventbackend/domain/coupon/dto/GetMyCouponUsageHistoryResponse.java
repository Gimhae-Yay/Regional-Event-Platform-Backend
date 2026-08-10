package io.regionevent.regioneventbackend.domain.coupon.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemptionStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.GetMyCouponUsageHistoryResult;

public record GetMyCouponUsageHistoryResponse(
    String couponId,
    List<UsageHistory> usageHistory
) {

    public static GetMyCouponUsageHistoryResponse from(GetMyCouponUsageHistoryResult result) {
        return new GetMyCouponUsageHistoryResponse(
            result.couponId().toString(),
            result.usageHistory().stream()
                .map(UsageHistory::from)
                .toList()
        );
    }

    public record UsageHistory(
        String couponRedemptionId,
        String reservationId,
        String priceSnapshotId,
        CouponRedemptionStatus status,
        long discountAmount,
        Instant confirmedAt,
        Instant reversedAt,
        String reversalReason
    ) {

        private static UsageHistory from(GetMyCouponUsageHistoryResult.UsageHistory usageHistory) {
            return new UsageHistory(
                usageHistory.couponRedemptionId().toString(),
                usageHistory.reservationId().toString(),
                usageHistory.priceSnapshotId().toString(),
                usageHistory.status(),
                usageHistory.discountAmount(),
                usageHistory.confirmedAt(),
                usageHistory.reversedAt(),
                usageHistory.reversalReason()
            );
        }
    }
}
