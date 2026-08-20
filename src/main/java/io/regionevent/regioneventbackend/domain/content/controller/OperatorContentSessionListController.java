package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.GetOperatorContentSessionsResponse;
import io.regionevent.regioneventbackend.domain.content.service.GetOperatorContentSessionsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.OperatorContentSessionListResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/contents")
public class OperatorContentSessionListController {

    private static final String SUCCESS_MESSAGE = "내 콘텐츠 회차 목록 조회에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final GetOperatorContentSessionsUseCase getOperatorContentSessionsUseCase;

    public OperatorContentSessionListController(
        GetOperatorContentSessionsUseCase getOperatorContentSessionsUseCase
    ) {
        this.getOperatorContentSessionsUseCase = getOperatorContentSessionsUseCase;
    }

    @GetMapping("/{contentId}/sessions")
    public ResponseEntity<ApiResponse<GetOperatorContentSessionsResponse>> getOperatorContentSessions(
        Authentication authentication,
        @PathVariable String contentId
    ) {
        OperatorContentSessionListResult result = getOperatorContentSessionsUseCase.get(
            toAuthenticatedUserId(authentication),
            toContentId(contentId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetOperatorContentSessionsResponse.from(result)
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
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return contentId;
    }
}
