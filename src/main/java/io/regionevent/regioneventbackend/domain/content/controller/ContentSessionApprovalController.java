package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.ApproveContentSessionResponse;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentSessionResult;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentSessionUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/sessions")
public class ContentSessionApprovalController {

    private static final String SUCCESS_MESSAGE = "회차 승인에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final ApproveContentSessionUseCase approveContentSessionUseCase;

    public ContentSessionApprovalController(ApproveContentSessionUseCase approveContentSessionUseCase) {
        this.approveContentSessionUseCase = approveContentSessionUseCase;
    }

    @PostMapping("/{sessionId}/approve")
    public ResponseEntity<ApiResponse<ApproveContentSessionResponse>> approveContentSession(
        Authentication authentication,
        @PathVariable String sessionId,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        ApproveContentSessionResult result = approveContentSessionUseCase.approve(
            toAuthenticatedUserId(authentication),
            toSessionId(sessionId),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            ApproveContentSessionResponse.from(result)
        ).toResponseEntity();
    }

    private Long toAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }

    private Long toSessionId(String value) {
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
        }
    }
}
