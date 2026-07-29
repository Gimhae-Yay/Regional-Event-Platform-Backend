package io.regionevent.regioneventbackend.domain.review.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.review.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {
}
