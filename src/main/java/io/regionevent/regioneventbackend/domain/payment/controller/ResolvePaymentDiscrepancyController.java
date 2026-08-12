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

import io.regionevent.regioneventbackend.domain.payment.dto.ResolvePaymentDiscrepancyRequest;
import io.regionevent.regioneventbackend.domain.payment.dto.ResolvePaymentDiscrepancyResponse;
import io.regionevent.regioneventbackend.domain.payment.service.ResolvePaymentDiscrepancyResult;
import io.regionevent.regioneventbackend.domain.payment.service.ResolvePaymentDiscrepancyUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/platform-admin/payment-discrepancies")
public class ResolvePaymentDiscrepancyController {

    private static final String SUCCESS_MESSAGE = "결제 불일치 문제없음 종결에 성공했습니다.";

    private final ResolvePaymentDiscrepancyUseCase resolvePaymentDiscrepancyUseCase;

    public ResolvePaymentDiscrepancyController(
        ResolvePaymentDiscrepancyUseCase resolvePaymentDiscrepancyUseCase
    ) {
        this.resolvePaymentDiscrepancyUseCase = resolvePaymentDiscrepancyUseCase;
    }

    @PostMapping("/{discrepancyId}/manual-actions")
    public ResponseEntity<ApiResponse<ResolvePaymentDiscrepancyResponse>> resolve(
        @AuthenticationPrincipal Long actorUserId,
        @PathVariable String discrepancyId,
        @Valid @RequestBody ResolvePaymentDiscrepancyRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        ResolvePaymentDiscrepancyResult result = resolvePaymentDiscrepancyUseCase.resolve(
            actorUserId,
            PaymentDiscrepancyIdParser.parseRequired(discrepancyId),
            request,
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            ResolvePaymentDiscrepancyResponse.from(result)
        ).toResponseEntity();
    }
}
