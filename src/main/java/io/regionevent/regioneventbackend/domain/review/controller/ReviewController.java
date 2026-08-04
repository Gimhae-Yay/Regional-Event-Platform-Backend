package io.regionevent.regioneventbackend.domain.review.controller;

import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.review.dto.UpdateReviewRequest;
import io.regionevent.regioneventbackend.domain.review.dto.UpdateReviewResponse;
import io.regionevent.regioneventbackend.domain.review.service.UpdateReviewUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final UpdateReviewUseCase updateReviewUseCase;

    public ReviewController(UpdateReviewUseCase updateReviewUseCase) {
        this.updateReviewUseCase = updateReviewUseCase;
    }

    @PatchMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<UpdateReviewResponse>> updateReview(
        @AuthenticationPrincipal Long userId,
        @PathVariable String reviewId,
        @Valid @RequestBody UpdateReviewRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        UpdateReviewResponse response = updateReviewUseCase.update(
            userId,
            toReviewId(reviewId),
            request,
            UUID.fromString(requestId)
        );
        return ApiResponse.success(HttpStatus.OK, "후기 수정에 성공했습니다.", response).toResponseEntity();
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
