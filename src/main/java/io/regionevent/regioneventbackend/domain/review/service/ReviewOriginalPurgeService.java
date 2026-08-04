package io.regionevent.regioneventbackend.domain.review.service;

import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.review.repository.ReviewRepository;

@Service
public class ReviewOriginalPurgeService {

    private static final Logger log = LoggerFactory.getLogger(ReviewOriginalPurgeService.class);
    private static final int BATCH_SIZE = 100;

    private final ReviewRepository reviewRepository;
    private final TransactionTemplate batchTransactionTemplate;

    public ReviewOriginalPurgeService(
        ReviewRepository reviewRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.reviewRepository = reviewRepository;
        batchTransactionTemplate = new TransactionTemplate(transactionManager);
        batchTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ReviewOriginalPurgeResult purgeDeletedReviewOriginals() {
        return purgeDeletedReviewOriginals(batchResult -> { });
    }

    ReviewOriginalPurgeResult purgeDeletedReviewOriginals(
        Consumer<ReviewOriginalPurgeResult> committedBatchConsumer
    ) {
        int batchCount = 0;
        int selectedReviewCount = 0;
        int purgedReviewCount = 0;

        while (true) {
            ReviewOriginalPurgeBatchResult batchResult = batchTransactionTemplate.execute(status -> purgeBatch());
            if (batchResult.selectedReviewCount() == 0) {
                return new ReviewOriginalPurgeResult(
                    batchCount,
                    selectedReviewCount,
                    purgedReviewCount,
                    selectedReviewCount - purgedReviewCount
                );
            }
            batchCount++;
            selectedReviewCount += batchResult.selectedReviewCount();
            purgedReviewCount += batchResult.purgedReviewCount();
            log.info(
                "Deleted review original purge batch finished. selectedReviewCount={}, purgedReviewCount={}, "
                    + "zeroUpdateCount={}",
                batchResult.selectedReviewCount(),
                batchResult.purgedReviewCount(),
                batchResult.zeroUpdateCount()
            );
            committedBatchConsumer.accept(new ReviewOriginalPurgeResult(
                1,
                batchResult.selectedReviewCount(),
                batchResult.purgedReviewCount(),
                batchResult.zeroUpdateCount()
            ));
            if (batchResult.selectedReviewCount() < BATCH_SIZE) {
                return new ReviewOriginalPurgeResult(
                    batchCount,
                    selectedReviewCount,
                    purgedReviewCount,
                    selectedReviewCount - purgedReviewCount
                );
            }
        }
    }

    private ReviewOriginalPurgeBatchResult purgeBatch() {
        List<Long> reviewIds = reviewRepository.findDeletedReviewIdsWithOriginal(PageRequest.of(0, BATCH_SIZE));
        int purgedReviewCount = reviewIds.stream()
            .mapToInt(reviewRepository::purgeDeletedReviewOriginalIfEligible)
            .sum();
        return new ReviewOriginalPurgeBatchResult(reviewIds.size(), purgedReviewCount);
    }

    private record ReviewOriginalPurgeBatchResult(
        int selectedReviewCount,
        int purgedReviewCount
    ) {

        int zeroUpdateCount() {
            return selectedReviewCount - purgedReviewCount;
        }
    }
}
