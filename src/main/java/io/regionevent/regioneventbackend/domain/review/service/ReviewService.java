package io.regionevent.regioneventbackend.domain.review.service;

import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.review.entity.Review;
import io.regionevent.regioneventbackend.domain.review.entity.ReviewStatus;
import io.regionevent.regioneventbackend.domain.review.repository.ReviewRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ReviewService {

    private static final String REVIEW_VISIT_UNIQUE_CONSTRAINT = "uk_review_visit";

    private final ReviewRepository reviewRepository;

    public ReviewService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    public Review createPublished(
        Visit visit,
        AppUser user,
        Integer rating,
        String reviewText
    ) {
        try {
            return reviewRepository.saveAndFlush(new Review(
                visit.getRegion(),
                visit,
                user,
                visit.getContent(),
                rating,
                reviewText,
                ReviewStatus.PUBLISHED,
                null
            ));
        } catch (DataIntegrityViolationException exception) {
            if (isReviewVisitUniqueConstraintViolation(exception)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
            }
            throw exception;
        }
    }

    public Review updatePublishedByAuthorWithinThirtyDays(
        Long reviewId,
        Long userId,
        Integer rating,
        String reviewText
    ) {
        int updatedCount = reviewRepository.updatePublishedByAuthorWithinThirtyDays(
            reviewId,
            userId,
            rating,
            reviewText
        );
        if (updatedCount == 1) {
            return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        }
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (review.getStatus() == ReviewStatus.DELETED) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    public Region findRegionByReviewId(Long reviewId) {
        return reviewRepository.findById(reviewId)
            .map(Review::getRegion)
            .orElse(null);
    }

    public Page<Review> findPublishedByContentId(Long contentId, Pageable pageable) {
        return reviewRepository.findByContentContentIdAndStatusOrderByCreatedAtDescReviewIdDesc(
            contentId,
            ReviewStatus.PUBLISHED,
            pageable
        );
    }

    private boolean isReviewVisitUniqueConstraintViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException
                && REVIEW_VISIT_UNIQUE_CONSTRAINT.equalsIgnoreCase(
                    constraintViolationException.getConstraintName()
                )) {
                return true;
            }
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(REVIEW_VISIT_UNIQUE_CONSTRAINT)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
