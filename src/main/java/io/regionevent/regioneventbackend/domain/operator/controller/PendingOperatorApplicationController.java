package io.regionevent.regioneventbackend.domain.operator.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.operator.dto.PendingOperatorApplicationsResponse;
import io.regionevent.regioneventbackend.domain.operator.service.GetPendingOperatorApplicationsUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/operator-requests")
public class PendingOperatorApplicationController {

    private static final String SUCCESS_MESSAGE = "운영자 승인 요청 대기 목록 조회에 성공했습니다.";

    private final GetPendingOperatorApplicationsUseCase getPendingOperatorApplicationsUseCase;

    public PendingOperatorApplicationController(
        GetPendingOperatorApplicationsUseCase getPendingOperatorApplicationsUseCase
    ) {
        this.getPendingOperatorApplicationsUseCase = getPendingOperatorApplicationsUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PendingOperatorApplicationsResponse>> getPendingApplications(
        @AuthenticationPrincipal Long userId,
        @RequestParam String status
    ) {
        PendingOperatorApplicationsResponse response = getPendingOperatorApplicationsUseCase.get(userId, status);
        return ApiResponse.success(HttpStatus.OK, SUCCESS_MESSAGE, response).toResponseEntity();
    }
}
