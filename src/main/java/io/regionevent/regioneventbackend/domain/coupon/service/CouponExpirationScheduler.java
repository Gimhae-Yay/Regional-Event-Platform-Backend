package io.regionevent.regioneventbackend.domain.coupon.service;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CouponExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(CouponExpirationScheduler.class);

    private final ExpireCouponsUseCase expireCouponsUseCase;
    private final Counter executionCounter;
    private final Counter batchCounter;
    private final Counter candidateCouponCounter;
    private final Counter expiredCouponCounter;
    private final Counter zeroUpdateCouponCounter;
    private final Counter failureCounter;
    private final Timer executionTimer;

    public CouponExpirationScheduler(ExpireCouponsUseCase expireCouponsUseCase, MeterRegistry meterRegistry) {
        this.expireCouponsUseCase = expireCouponsUseCase;
        this.executionCounter = meterRegistry.counter("coupon.expiration.execution");
        this.batchCounter = meterRegistry.counter("coupon.expiration.batch");
        this.candidateCouponCounter = meterRegistry.counter("coupon.expiration.candidate");
        this.expiredCouponCounter = meterRegistry.counter("coupon.expiration.expired");
        this.zeroUpdateCouponCounter = meterRegistry.counter("coupon.expiration.zero-update");
        this.failureCounter = meterRegistry.counter("coupon.expiration.failure");
        this.executionTimer = meterRegistry.timer("coupon.expiration.execution.time");
    }

    @Scheduled(cron = "${coupon.expiration.cron:0 */5 * * * *}")
    public void expireCoupons() {
        long startedAt = System.nanoTime();
        log.info("Coupon expiration scheduler started.");

        try {
            CouponExpirationResult result = expireCouponsUseCase.execute();
            recordMetrics(result);
            log.atInfo()
                .addKeyValue("requestId", result.requestId())
                .addKeyValue("processedBatchCount", result.processedBatchCount())
                .addKeyValue("candidateCouponCount", result.candidateCouponCount())
                .addKeyValue("expiredCouponCount", result.expiredCouponCount())
                .addKeyValue("skippedCouponCount", result.skippedCouponCount())
                .addKeyValue("failedBatchCount", result.failedBatchCount())
                .addKeyValue("elapsedMillis", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt))
                .log("Coupon expiration scheduler finished.");
        } catch (RuntimeException exception) {
            failureCounter.increment();
            log.error("Coupon expiration scheduler failed.", exception);
            throw exception;
        } finally {
            executionTimer.record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
        }
    }

    private void recordMetrics(CouponExpirationResult result) {
        executionCounter.increment();
        batchCounter.increment(result.processedBatchCount());
        candidateCouponCounter.increment(result.candidateCouponCount());
        expiredCouponCounter.increment(result.expiredCouponCount());
        zeroUpdateCouponCounter.increment(result.skippedCouponCount());
        failureCounter.increment(result.failedBatchCount());
    }
}
