package io.regionevent.regioneventbackend.domain.operator.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.operator.dto.CreateOperatorApplicationRequest;
import io.regionevent.regioneventbackend.domain.operator.dto.CreateOperatorApplicationResponse;
import io.regionevent.regioneventbackend.domain.operator.service.ReapplyOperatorApplicationUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/operator-requests")
public class OperatorApplicationController {

    private static final String CREATE_SUCCESS_MESSAGE = "운영자 권한 신청에 성공했습니다.";

    private final ReapplyOperatorApplicationUseCase reapplyOperatorApplicationUseCase;

    public OperatorApplicationController(ReapplyOperatorApplicationUseCase reapplyOperatorApplicationUseCase) {
        this.reapplyOperatorApplicationUseCase = reapplyOperatorApplicationUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateOperatorApplicationResponse>> create(
        @AuthenticationPrincipal Long userId,
        @Valid @RequestBody CreateOperatorApplicationRequest request
    ) {
        CreateOperatorApplicationResponse response = reapplyOperatorApplicationUseCase.reapply(userId, request);
        return ApiResponse.success(HttpStatus.CREATED, CREATE_SUCCESS_MESSAGE, response).toResponseEntity();
    }
}
