package io.regionevent.regioneventbackend.domain.review.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.review.entity.Review;

public record CreateVisitReviewResponse(
    String reviewId,
    String visitId,
    String contentId,
    Integer rating,
    String reviewText,
    Instant createdAt
) {

    public static CreateVisitReviewResponse from(Review review) {
        return new CreateVisitReviewResponse(
            review.getReviewId().toString(),
            review.getVisit().getVisitId().toString(),
            review.getContent().getContentId().toString(),
            review.getRating(),
            review.getReviewText(),
            review.getCreatedAt()
        );
    }
}
