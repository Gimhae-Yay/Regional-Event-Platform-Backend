package io.regionevent.regioneventbackend.domain.coupon.service;

import java.util.UUID;

public record CouponExpirationResult(
    UUID requestId,
    int processedBatchCount,
    int candidateCouponCount,
    int expiredCouponCount,
    int skippedCouponCount,
    int failedBatchCount
) {
}
