package io.regionevent.regioneventbackend.domain.review.controller;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.review.dto.GetPublicContentReviewsResponse;
import io.regionevent.regioneventbackend.domain.review.service.GetPublicContentReviewsUseCase;
import io.regionevent.regioneventbackend.domain.review.service.PublicContentReviewListResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/contents")
public class PublicContentReviewController {

    private static final String SUCCESS_MESSAGE = "인증 후기 목록 조회에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final GetPublicContentReviewsUseCase getPublicContentReviewsUseCase;

    public PublicContentReviewController(GetPublicContentReviewsUseCase getPublicContentReviewsUseCase) {
        this.getPublicContentReviewsUseCase = getPublicContentReviewsUseCase;
    }

    @GetMapping("/{contentId}/reviews")
    public ResponseEntity<ApiResponse<GetPublicContentReviewsResponse>> getPublicContentReviews(
        @PathVariable String contentId,
        @RequestParam(defaultValue = "0") String page,
        @RequestParam(defaultValue = "20") String size
    ) {
        PublicContentReviewListResult result = getPublicContentReviewsUseCase.get(
            toContentId(contentId),
            toPage(page),
            toSize(size)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetPublicContentReviewsResponse.from(result)
        ).toResponseEntity();
    }

    private Long toContentId(String value) {
        Long contentId = toLong(value);
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return contentId;
    }

    private int toPage(String value) {
        int page = toInt(value);
        if (page < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return page;
    }

    private int toSize(String value) {
        int size = toInt(value);
        if (size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return size;
    }

    private Long toLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
    }

    private int toInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
    }
}
