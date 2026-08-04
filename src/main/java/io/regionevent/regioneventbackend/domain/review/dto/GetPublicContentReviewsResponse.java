package io.regionevent.regioneventbackend.domain.review.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.review.service.PublicContentReviewListResult;

public record GetPublicContentReviewsResponse(
    List<ReviewResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

    public GetPublicContentReviewsResponse {
        content = List.copyOf(content);
    }

    public static GetPublicContentReviewsResponse from(PublicContentReviewListResult result) {
        return new GetPublicContentReviewsResponse(
            result.content().stream().map(ReviewResponse::from).toList(),
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages()
        );
    }

    public record ReviewResponse(
        String reviewId,
        String authorDisplayName,
        Integer rating,
        String reviewText,
        Instant createdAt,
        Instant updatedAt
    ) {

        private static ReviewResponse from(PublicContentReviewListResult.Review review) {
            return new ReviewResponse(
                review.reviewId().toString(),
                review.authorDisplayName(),
                review.rating(),
                review.reviewText(),
                review.createdAt(),
                review.updatedAt()
            );
        }
    }
}
