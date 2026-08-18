package io.regionevent.regioneventbackend.domain.content.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.PendingContentWithdrawalRequestsResponse;
import io.regionevent.regioneventbackend.domain.content.service.GetPendingContentWithdrawalRequestsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.PendingContentWithdrawalRequestListResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/content-withdrawal-requests")
public class PendingContentWithdrawalRequestController {

    private static final String SUCCESS_MESSAGE =
        "전체 콘텐츠 철회 요청 대기 목록 조회에 성공했습니다.";

    private final GetPendingContentWithdrawalRequestsUseCase getPendingRequestsUseCase;

    public PendingContentWithdrawalRequestController(
        GetPendingContentWithdrawalRequestsUseCase getPendingRequestsUseCase
    ) {
        this.getPendingRequestsUseCase = getPendingRequestsUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PendingContentWithdrawalRequestsResponse>> getPendingRequests(
        Authentication authentication,
        @RequestParam(required = false) String status
    ) {
        PendingContentWithdrawalRequestListResult result = getPendingRequestsUseCase.get(
            toAuthenticatedUserId(authentication),
            status
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            PendingContentWithdrawalRequestsResponse.from(result)
        ).toResponseEntity();
    }

    private Long toAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
