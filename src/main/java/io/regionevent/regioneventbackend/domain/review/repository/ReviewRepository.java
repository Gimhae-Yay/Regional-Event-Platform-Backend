package io.regionevent.regioneventbackend.domain.review.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.review.entity.Review;
import io.regionevent.regioneventbackend.domain.review.entity.ReviewStatus;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select review from Review review where review.reviewId = ?1")
    Optional<Review> findByReviewIdForUpdate(Long reviewId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Review review
        SET review.rating = COALESCE(:rating, review.rating),
            review.reviewText = COALESCE(:reviewText, review.reviewText),
            review.updatedAt = cast(CURRENT_TIMESTAMP as instant)
        WHERE review.reviewId = :reviewId
          AND review.user.userId = :userId
          AND review.authorUnlinkedAt IS NULL
          AND review.status = io.regionevent.regioneventbackend.domain.review.entity.ReviewStatus.PUBLISHED
          AND CURRENT_TIMESTAMP < timestampadd(day, 30, review.createdAt)
          AND EXISTS (
              SELECT user
              FROM AppUser user
              WHERE user.userId = :userId
                AND user.status = io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus.ACTIVE
          )
        """)
    int updatePublishedByAuthorWithinThirtyDays(
        @Param("reviewId") Long reviewId,
        @Param("userId") Long userId,
        @Param("rating") Integer rating,
        @Param("reviewText") String reviewText
    );

    Page<Review> findByContentContentIdAndStatusOrderByCreatedAtDescReviewIdDesc(
        Long contentId,
        ReviewStatus status,
        Pageable pageable
    );
}
