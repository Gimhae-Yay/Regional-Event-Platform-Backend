package io.regionevent.regioneventbackend.domain.operator.controller;

import static io.regionevent.regioneventbackend.domain.operator.controller.OperatorApplicationIdParser.toOperatorApplicationId;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.operator.dto.ApproveOperatorApplicationResponse;
import io.regionevent.regioneventbackend.domain.operator.service.ApproveOperatorApplicationUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/operator-requests")
public class OperatorApplicationApprovalController {

    private static final String SUCCESS_MESSAGE = "운영자 승인에 성공했습니다.";

    private final ApproveOperatorApplicationUseCase approveOperatorApplicationUseCase;

    public OperatorApplicationApprovalController(
        ApproveOperatorApplicationUseCase approveOperatorApplicationUseCase
    ) {
        this.approveOperatorApplicationUseCase = approveOperatorApplicationUseCase;
    }

    @PostMapping("/{operatorApplicationId}/approve")
    public ResponseEntity<ApiResponse<ApproveOperatorApplicationResponse>> approve(
        @AuthenticationPrincipal Long userId,
        @PathVariable String operatorApplicationId,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        ApproveOperatorApplicationResponse response = approveOperatorApplicationUseCase.approve(
            userId,
            toOperatorApplicationId(operatorApplicationId),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(HttpStatus.OK, SUCCESS_MESSAGE, response).toResponseEntity();
    }
}
