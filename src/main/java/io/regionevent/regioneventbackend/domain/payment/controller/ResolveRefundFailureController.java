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

import io.regionevent.regioneventbackend.domain.payment.dto.ResolveRefundFailureRequest;
import io.regionevent.regioneventbackend.domain.payment.dto.ResolveRefundFailureResponse;
import io.regionevent.regioneventbackend.domain.payment.service.ResolveRefundFailureResult;
import io.regionevent.regioneventbackend.domain.payment.service.ResolveRefundFailureUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/platform-admin/refund-failures")
public class ResolveRefundFailureController {

    private static final String SUCCESS_MESSAGE = "환불 실패 수동 조치에 성공했습니다.";

    private final ResolveRefundFailureUseCase resolveRefundFailureUseCase;

    public ResolveRefundFailureController(ResolveRefundFailureUseCase resolveRefundFailureUseCase) {
        this.resolveRefundFailureUseCase = resolveRefundFailureUseCase;
    }

    @PostMapping("/{refundId}/manual-actions")
    public ResponseEntity<ApiResponse<ResolveRefundFailureResponse>> resolve(
        @AuthenticationPrincipal Long actorUserId,
        @PathVariable String refundId,
        @Valid @RequestBody ResolveRefundFailureRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        ResolveRefundFailureResult result = resolveRefundFailureUseCase.resolve(
            actorUserId,
            RefundFailureIdParser.parseRequired(refundId),
            request,
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            ResolveRefundFailureResponse.from(result)
        ).toResponseEntity();
    }
}
