package io.regionevent.regioneventbackend.domain.review.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.review.dto.CreateVisitReviewRequest;
import io.regionevent.regioneventbackend.domain.review.dto.CreateVisitReviewResponse;
import io.regionevent.regioneventbackend.domain.review.service.CreateVisitReviewUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/visits")
public class VisitReviewController {

    private static final String CREATE_REVIEW_SUCCESS_MESSAGE = "후기 작성에 성공했습니다.";

    private final CreateVisitReviewUseCase createVisitReviewUseCase;

    public VisitReviewController(CreateVisitReviewUseCase createVisitReviewUseCase) {
        this.createVisitReviewUseCase = createVisitReviewUseCase;
    }

    @PostMapping("/{visitId}/reviews")
    public ResponseEntity<ApiResponse<CreateVisitReviewResponse>> createReview(
        @AuthenticationPrincipal Long userId,
        @Positive @PathVariable Long visitId,
        @Valid @RequestBody CreateVisitReviewRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        CreateVisitReviewResponse response = createVisitReviewUseCase.create(
            userId,
            visitId,
            request,
            UUID.fromString(requestId)
        );
        return ApiResponse.success(HttpStatus.CREATED, CREATE_REVIEW_SUCCESS_MESSAGE, response).toResponseEntity();
    }

}
