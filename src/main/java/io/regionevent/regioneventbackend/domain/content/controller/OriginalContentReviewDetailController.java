package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.OriginalContentReviewDetailResponse;
import io.regionevent.regioneventbackend.domain.content.service.GetOriginalContentReviewDetailUseCase;
import io.regionevent.regioneventbackend.domain.content.service.OriginalContentReviewDetailResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/contents")
public class OriginalContentReviewDetailController {

    private static final String SUCCESS_MESSAGE = "승인 검토 콘텐츠 상세 조회에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final GetOriginalContentReviewDetailUseCase getOriginalContentReviewDetailUseCase;

    public OriginalContentReviewDetailController(
        GetOriginalContentReviewDetailUseCase getOriginalContentReviewDetailUseCase
    ) {
        this.getOriginalContentReviewDetailUseCase = getOriginalContentReviewDetailUseCase;
    }

    @GetMapping("/{contentId}")
    public ResponseEntity<ApiResponse<OriginalContentReviewDetailResponse>> getReviewDetail(
        Authentication authentication,
        @PathVariable String contentId
    ) {
        OriginalContentReviewDetailResult result = getOriginalContentReviewDetailUseCase.get(
            toAuthenticatedUserId(authentication),
            toContentId(contentId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            OriginalContentReviewDetailResponse.from(result)
        ).toResponseEntity();
    }

    private Long toAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }

    private Long toContentId(String value) {
        Long contentId;
        try {
            contentId = Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return contentId;
    }
}
