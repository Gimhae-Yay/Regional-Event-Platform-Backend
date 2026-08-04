package io.regionevent.regioneventbackend.domain.content.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.PendingContentSessionsResponse;
import io.regionevent.regioneventbackend.domain.content.service.GetPendingContentSessionsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.PendingContentSessionListResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/sessions")
public class PendingContentSessionController {

    private static final String SUCCESS_MESSAGE = "심사 대기 회차 목록 조회에 성공했습니다.";

    private final GetPendingContentSessionsUseCase getPendingContentSessionsUseCase;

    public PendingContentSessionController(GetPendingContentSessionsUseCase getPendingContentSessionsUseCase) {
        this.getPendingContentSessionsUseCase = getPendingContentSessionsUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PendingContentSessionsResponse>> getPendingSessions(
        Authentication authentication,
        @RequestParam String status
    ) {
        PendingContentSessionListResult result = getPendingContentSessionsUseCase.get(
            toAuthenticatedUserId(authentication),
            status
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            PendingContentSessionsResponse.from(result)
        ).toResponseEntity();
    }

    private Long toAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
