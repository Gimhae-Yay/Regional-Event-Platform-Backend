package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.RejectContentSessionRequest;
import io.regionevent.regioneventbackend.domain.content.dto.RejectContentSessionResponse;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentSessionResult;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentSessionUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/sessions")
public class ContentSessionRejectionController {

    private static final String SUCCESS_MESSAGE = "회차 반려에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final RejectContentSessionUseCase rejectContentSessionUseCase;

    public ContentSessionRejectionController(RejectContentSessionUseCase rejectContentSessionUseCase) {
        this.rejectContentSessionUseCase = rejectContentSessionUseCase;
    }

    @PostMapping("/{sessionId}/reject")
    public ResponseEntity<ApiResponse<RejectContentSessionResponse>> reject(
        Authentication authentication,
        @PathVariable String sessionId,
        @Valid @RequestBody RejectContentSessionRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        RejectContentSessionResult result = rejectContentSessionUseCase.reject(
            userId,
            toSessionId(sessionId),
            request.reason(),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            RejectContentSessionResponse.from(result)
        ).toResponseEntity();
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
