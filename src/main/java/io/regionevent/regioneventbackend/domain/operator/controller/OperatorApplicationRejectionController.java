package io.regionevent.regioneventbackend.domain.operator.controller;

import static io.regionevent.regioneventbackend.domain.operator.controller.OperatorApplicationIdParser.toOperatorApplicationId;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.operator.dto.RejectOperatorApplicationRequest;
import io.regionevent.regioneventbackend.domain.operator.dto.RejectOperatorApplicationResponse;
import io.regionevent.regioneventbackend.domain.operator.service.RejectOperatorApplicationUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/operator-requests")
public class OperatorApplicationRejectionController {

    private static final String SUCCESS_MESSAGE = "운영자 신청 반려에 성공했습니다.";

    private final RejectOperatorApplicationUseCase rejectOperatorApplicationUseCase;

    public OperatorApplicationRejectionController(
        RejectOperatorApplicationUseCase rejectOperatorApplicationUseCase
    ) {
        this.rejectOperatorApplicationUseCase = rejectOperatorApplicationUseCase;
    }

    @PostMapping("/{operatorApplicationId}/reject")
    public ResponseEntity<ApiResponse<RejectOperatorApplicationResponse>> reject(
        @AuthenticationPrincipal Long userId,
        @PathVariable String operatorApplicationId,
        @Valid @RequestBody RejectOperatorApplicationRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        RejectOperatorApplicationResponse response = rejectOperatorApplicationUseCase.reject(
            userId,
            toOperatorApplicationId(operatorApplicationId),
            request.rejectedReason(),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(HttpStatus.OK, SUCCESS_MESSAGE, response).toResponseEntity();
    }
}
