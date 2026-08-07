package io.regionevent.regioneventbackend.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.function.Consumer;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

class ReviewOriginalPurgeSchedulerTest {

    @Test
    void purgeDeletedReviewOriginals_whenFollowingBatchFails_recordsCommittedBatchMetrics() {
        ReviewOriginalPurgeService reviewOriginalPurgeService = mock(ReviewOriginalPurgeService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ReviewOriginalPurgeScheduler scheduler = new ReviewOriginalPurgeScheduler(
            reviewOriginalPurgeService,
            meterRegistry
        );
        doAnswer(invocation -> {
            Consumer<ReviewOriginalPurgeResult> committedBatchConsumer = invocation.getArgument(0);
            committedBatchConsumer.accept(new ReviewOriginalPurgeResult(1, 100, 100, 0));
            throw new IllegalStateException("batch failed");
        }).when(reviewOriginalPurgeService).purgeDeletedReviewOriginals(any());

        assertThatThrownBy(scheduler::purgeDeletedReviewOriginals)
            .isInstanceOf(IllegalStateException.class);

        assertThat(meterRegistry.counter("review.original-purge.batch").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("review.original-purge.review.purged").count()).isEqualTo(100);
        assertThat(meterRegistry.counter("review.original-purge.review.zero-update").count()).isZero();
        assertThat(meterRegistry.counter("review.original-purge.failure").count()).isEqualTo(1);
    }
}
