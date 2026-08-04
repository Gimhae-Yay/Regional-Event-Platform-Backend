package io.regionevent.regioneventbackend.domain.review.service;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReviewOriginalPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReviewOriginalPurgeScheduler.class);

    private final ReviewOriginalPurgeService reviewOriginalPurgeService;
    private final Counter executionCounter;
    private final Counter batchCounter;
    private final Counter purgedReviewCounter;
    private final Counter zeroUpdateCounter;
    private final Counter failureCounter;
    private final Timer executionTimer;

    public ReviewOriginalPurgeScheduler(
        ReviewOriginalPurgeService reviewOriginalPurgeService,
        MeterRegistry meterRegistry
    ) {
        this.reviewOriginalPurgeService = reviewOriginalPurgeService;
        executionCounter = meterRegistry.counter("review.original-purge.execution");
        batchCounter = meterRegistry.counter("review.original-purge.batch");
        purgedReviewCounter = meterRegistry.counter("review.original-purge.review.purged");
        zeroUpdateCounter = meterRegistry.counter("review.original-purge.review.zero-update");
        failureCounter = meterRegistry.counter("review.original-purge.failure");
        executionTimer = meterRegistry.timer("review.original-purge.execution.time");
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void purgeDeletedReviewOriginals() {
        log.info("Deleted review original purge scheduler started.");
        long startedAt = System.nanoTime();

        try {
            ReviewOriginalPurgeResult result = reviewOriginalPurgeService.purgeDeletedReviewOriginals(
                this::recordCommittedBatchMetrics
            );
            executionCounter.increment();
            log.info(
                "Deleted review original purge scheduler finished. batchCount={}, selectedReviewCount={}, "
                    + "purgedReviewCount={}, zeroUpdateCount={}, elapsedMillis={}",
                result.batchCount(),
                result.selectedReviewCount(),
                result.purgedReviewCount(),
                result.zeroUpdateCount(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            );
        } catch (RuntimeException exception) {
            failureCounter.increment();
            log.error("Deleted review original purge scheduler failed.", exception);
            throw exception;
        } finally {
            executionTimer.record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
        }
    }

    private void recordCommittedBatchMetrics(ReviewOriginalPurgeResult batchResult) {
        batchCounter.increment(batchResult.batchCount());
        purgedReviewCounter.increment(batchResult.purgedReviewCount());
        zeroUpdateCounter.increment(batchResult.zeroUpdateCount());
    }
}
