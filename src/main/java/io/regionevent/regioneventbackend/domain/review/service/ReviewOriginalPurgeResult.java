package io.regionevent.regioneventbackend.domain.review.service;

public record ReviewOriginalPurgeResult(
    int batchCount,
    int selectedReviewCount,
    int purgedReviewCount,
    int zeroUpdateCount
) {
}
