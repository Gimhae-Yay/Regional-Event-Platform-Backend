package io.regionevent.regioneventbackend.domain.review.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.review.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE review
        SET user_id = NULL,
            author_unlinked_at = CURRENT_TIMESTAMP
        WHERE user_id = :userId
        """, nativeQuery = true)
    int unlinkAuthorByUserId(@Param("userId") Long userId);
}
