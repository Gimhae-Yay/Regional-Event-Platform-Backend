package io.regionevent.regioneventbackend.domain.review.service;

import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.review.entity.Review;
import io.regionevent.regioneventbackend.domain.review.entity.ReviewStatus;
import io.regionevent.regioneventbackend.domain.review.repository.ReviewRepository;
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

    @Transactional(propagation = Propagation.MANDATORY)
    public void unlinkAuthorByUserId(Long userId) {
        reviewRepository.unlinkAuthorByUserId(userId);
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
