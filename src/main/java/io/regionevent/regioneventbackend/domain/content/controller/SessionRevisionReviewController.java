package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.SessionRevisionReviewDetailResponse;
import io.regionevent.regioneventbackend.domain.content.service.GetSessionRevisionReviewDetailUseCase;
import io.regionevent.regioneventbackend.domain.content.service.SessionRevisionReviewDetailResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/session-revisions")
public class SessionRevisionReviewController {

    private static final String SUCCESS_MESSAGE = "심사 대기 회차 수정 요청 상세 조회에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final GetSessionRevisionReviewDetailUseCase getSessionRevisionReviewDetailUseCase;

    public SessionRevisionReviewController(
        GetSessionRevisionReviewDetailUseCase getSessionRevisionReviewDetailUseCase
    ) {
        this.getSessionRevisionReviewDetailUseCase = getSessionRevisionReviewDetailUseCase;
    }

    @GetMapping("/{revisionId}")
    public ResponseEntity<ApiResponse<SessionRevisionReviewDetailResponse>> getReviewDetail(
        Authentication authentication,
        @PathVariable String revisionId
    ) {
        SessionRevisionReviewDetailResult result = getSessionRevisionReviewDetailUseCase.get(
            toAuthenticatedUserId(authentication),
            toRevisionId(revisionId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            SessionRevisionReviewDetailResponse.from(result)
        ).toResponseEntity();
    }

    private Long toAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }

    private Long toRevisionId(String value) {
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
