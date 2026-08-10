package io.regionevent.regioneventbackend.domain.coupon.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CouponExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(CouponExpirationScheduler.class);

    private final ExpireCouponsUseCase expireCouponsUseCase;

    public CouponExpirationScheduler(ExpireCouponsUseCase expireCouponsUseCase) {
        this.expireCouponsUseCase = expireCouponsUseCase;
    }

    @Scheduled(cron = "${coupon.expiration.cron:0 */5 * * * *}")
    public void expireCoupons() {
        log.info("Coupon expiration scheduler started.");
        CouponExpirationResult result = expireCouponsUseCase.execute();
        log.info(
            "Coupon expiration scheduler finished. requestId={}, processedBatchCount={}, "
                + "candidateCouponCount={}, expiredCouponCount={}, skippedCouponCount={}, failedBatchCount={}",
            result.requestId(),
            result.processedBatchCount(),
            result.candidateCouponCount(),
            result.expiredCouponCount(),
            result.skippedCouponCount(),
            result.failedBatchCount()
        );
    }
}
