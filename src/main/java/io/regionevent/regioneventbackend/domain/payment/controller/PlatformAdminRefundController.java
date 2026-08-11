package io.regionevent.regioneventbackend.domain.payment.controller;

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

import io.regionevent.regioneventbackend.domain.payment.dto.CreateRefundRequest;
import io.regionevent.regioneventbackend.domain.payment.dto.CreateRefundResponse;
import io.regionevent.regioneventbackend.domain.payment.service.CreateRefundUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/platform-admin/payments")
public class PlatformAdminRefundController {

    private static final String SUCCESS_MESSAGE = "수동 환불에 성공했습니다.";

    private final CreateRefundUseCase createRefundUseCase;

    public PlatformAdminRefundController(CreateRefundUseCase createRefundUseCase) {
        this.createRefundUseCase = createRefundUseCase;
    }

    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<ApiResponse<CreateRefundResponse>> create(
        @AuthenticationPrincipal Long actorUserId,
        @PathVariable String paymentId,
        @Valid @RequestBody CreateRefundRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        CreateRefundResponse response = createRefundUseCase.create(
            actorUserId,
            paymentId,
            request,
            UUID.fromString(requestId)
        );
        return ApiResponse.success(HttpStatus.CREATED, SUCCESS_MESSAGE, response).toResponseEntity();
    }
}
