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

import io.regionevent.regioneventbackend.domain.content.dto.ApproveSessionRevisionResponse;
import io.regionevent.regioneventbackend.domain.content.service.ApproveSessionRevisionResult;
import io.regionevent.regioneventbackend.domain.content.service.ApproveSessionRevisionUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/session-revisions")
public class SessionRevisionApprovalController {

    private static final String SUCCESS_MESSAGE = "회차 수정 요청 승인에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final ApproveSessionRevisionUseCase approveSessionRevisionUseCase;

    public SessionRevisionApprovalController(
        ApproveSessionRevisionUseCase approveSessionRevisionUseCase
    ) {
        this.approveSessionRevisionUseCase = approveSessionRevisionUseCase;
    }

    @PostMapping("/{revisionId}/approve")
    public ResponseEntity<ApiResponse<ApproveSessionRevisionResponse>> approveSessionRevision(
        Authentication authentication,
        @PathVariable String revisionId,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        ApproveSessionRevisionResult result = approveSessionRevisionUseCase.approve(
            toAuthenticatedUserId(authentication),
            toRevisionId(revisionId),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            ApproveSessionRevisionResponse.from(result)
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
            throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
        }
    }
}
