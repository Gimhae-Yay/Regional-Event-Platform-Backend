package io.regionevent.regioneventbackend.domain.review.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.regionevent.regioneventbackend.domain.review.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select review from Review review where review.reviewId = ?1")
    Optional<Review> findByReviewIdForUpdate(Long reviewId);
}
