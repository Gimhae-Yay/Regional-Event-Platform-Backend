package io.regionevent.regioneventbackend.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.review.entity.Review;
import io.regionevent.regioneventbackend.domain.review.entity.ReviewStatus;
import io.regionevent.regioneventbackend.domain.review.repository.ReviewRepository;
import io.regionevent.regioneventbackend.domain.review.service.ReviewService.ReviewReadResult;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;

class ReviewServiceTest {

    @Test
    void 방문_식별자별_후기_읽기_결과를_조립한다() {
        ReviewRepository reviewRepository = mock(ReviewRepository.class);
        Review review = mock(Review.class);
        Visit visit = mock(Visit.class);
        Instant createdAt = Instant.parse("2026-08-03T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-08-04T00:00:00Z");
        when(reviewRepository.findAllByVisitVisitIdIn(List.of(11L))).thenReturn(List.of(review));
        when(review.getReviewId()).thenReturn(21L);
        when(review.getVisit()).thenReturn(visit);
        when(visit.getVisitId()).thenReturn(11L);
        when(review.getStatus()).thenReturn(ReviewStatus.DELETED);
        when(review.getRating()).thenReturn(null);
        when(review.getCreatedAt()).thenReturn(createdAt);
        when(review.getUpdatedAt()).thenReturn(updatedAt);
        ReviewService reviewService = new ReviewService(reviewRepository);

        Map<Long, ReviewReadResult> result = reviewService.findAllByVisitIds(List.of(11L));

        assertThat(result).containsOnlyKeys(11L);
        assertThat(result.get(11L)).isEqualTo(new ReviewReadResult(
            21L,
            11L,
            ReviewStatus.DELETED,
            null,
            null,
            createdAt,
            updatedAt
        ));
        verify(reviewRepository).findAllByVisitVisitIdIn(List.of(11L));
    }

    @Test
    void 방문_식별자가_없으면_저장소를_조회하지_않는다() {
        ReviewRepository reviewRepository = mock(ReviewRepository.class);
        ReviewService reviewService = new ReviewService(reviewRepository);

        Map<Long, ReviewReadResult> result = reviewService.findAllByVisitIds(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(reviewRepository);
    }
}
