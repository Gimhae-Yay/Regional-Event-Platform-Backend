package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.PendingSessionReviewDetailResponse;
import io.regionevent.regioneventbackend.domain.content.service.GetPendingSessionReviewDetailUseCase;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/sessions")
public class PendingSessionReviewController {
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");
    private final GetPendingSessionReviewDetailUseCase useCase;

    public PendingSessionReviewController(GetPendingSessionReviewDetailUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<PendingSessionReviewDetailResponse>> get(Authentication authentication, @PathVariable String sessionId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        if (!POSITIVE_DECIMAL_PATTERN.matcher(sessionId).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Long parsedSessionId;
        try {
            parsedSessionId = Long.valueOf(sessionId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
        }
        PendingSessionReviewDetailResponse response = PendingSessionReviewDetailResponse.from(useCase.get(userId, parsedSessionId));
        return ApiResponse.success(HttpStatus.OK, "심사 대기 회차 상세 조회에 성공했습니다.", response).toResponseEntity();
    }
}
