package io.regionevent.regioneventbackend.domain.review.service;

import java.time.Instant;
import java.util.List;

public record PublicContentReviewListResult(
    List<Review> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

    public PublicContentReviewListResult {
        content = List.copyOf(content);
    }

    public record Review(
        Long reviewId,
        String authorDisplayName,
        Integer rating,
        String reviewText,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
