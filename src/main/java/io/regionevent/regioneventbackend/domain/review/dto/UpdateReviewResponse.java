package io.regionevent.regioneventbackend.domain.review.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.review.entity.Review;

public record UpdateReviewResponse(
    String reviewId,
    Integer rating,
    String reviewText,
    Instant createdAt,
    Instant updatedAt
) {

    public static UpdateReviewResponse from(Review review) {
        return new UpdateReviewResponse(
            review.getReviewId().toString(),
            review.getRating(),
            review.getReviewText(),
            review.getCreatedAt(),
            review.getUpdatedAt()
        );
    }
}
