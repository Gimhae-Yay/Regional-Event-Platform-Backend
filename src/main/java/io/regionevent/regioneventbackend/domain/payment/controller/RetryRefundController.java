package io.regionevent.regioneventbackend.domain.payment.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.payment.dto.RetryRefundResponse;
import io.regionevent.regioneventbackend.domain.payment.service.RetryRefundUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/platform-admin/refunds")
public class RetryRefundController {

    private static final String SUCCESS_MESSAGE = "환불 재시도에 성공했습니다.";

    private final RetryRefundUseCase retryRefundUseCase;

    public RetryRefundController(RetryRefundUseCase retryRefundUseCase) {
        this.retryRefundUseCase = retryRefundUseCase;
    }

    @PostMapping("/{refundId}/retry")
    public ResponseEntity<ApiResponse<RetryRefundResponse>> retry(
        @AuthenticationPrincipal Long actorUserId,
        @PathVariable String refundId,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        RetryRefundResponse response = retryRefundUseCase.retry(
            actorUserId,
            refundId,
            UUID.fromString(requestId)
        );
        return ApiResponse.success(HttpStatus.OK, SUCCESS_MESSAGE, response).toResponseEntity();
    }
}
