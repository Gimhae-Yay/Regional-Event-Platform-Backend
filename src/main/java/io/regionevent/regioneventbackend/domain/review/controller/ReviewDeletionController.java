package io.regionevent.regioneventbackend.domain.review.controller;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.review.service.DeleteReviewUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewDeletionController {

    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final DeleteReviewUseCase deleteReviewUseCase;

    public ReviewDeletionController(DeleteReviewUseCase deleteReviewUseCase) {
        this.deleteReviewUseCase = deleteReviewUseCase;
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
        @AuthenticationPrincipal Long userId,
        @PathVariable String reviewId,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        deleteReviewUseCase.delete(userId, toReviewId(reviewId), UUID.fromString(requestId));
        return ResponseEntity.noContent().build();
    }

    private Long toReviewId(String value) {
        Long reviewId;
        try {
            reviewId = Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return reviewId;
    }
}
