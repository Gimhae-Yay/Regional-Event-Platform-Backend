package io.regionevent.regioneventbackend.domain.review.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.review.entity.Review;
import io.regionevent.regioneventbackend.domain.review.entity.ReviewStatus;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByContentContentIdAndStatusOrderByCreatedAtDescReviewIdDesc(
        Long contentId,
        ReviewStatus status,
        Pageable pageable
    );
}
