package io.regionevent.regioneventbackend.domain.payment.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.payment.dto.GetPaymentDiscrepancyResponse;
import io.regionevent.regioneventbackend.domain.payment.service.GetPaymentDiscrepancyUseCase;
import io.regionevent.regioneventbackend.domain.payment.service.PaymentDiscrepancyDetailInfo;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/platform-admin/payment-discrepancies")
public class PaymentDiscrepancyDetailController {

    private static final String SUCCESS_MESSAGE = "결제 불일치 상세 조회에 성공했습니다.";

    private final GetPaymentDiscrepancyUseCase getPaymentDiscrepancyUseCase;

    public PaymentDiscrepancyDetailController(GetPaymentDiscrepancyUseCase getPaymentDiscrepancyUseCase) {
        this.getPaymentDiscrepancyUseCase = getPaymentDiscrepancyUseCase;
    }

    @GetMapping("/{discrepancyId}")
    public ResponseEntity<ApiResponse<GetPaymentDiscrepancyResponse>> getDiscrepancy(
        @AuthenticationPrincipal Long actorUserId,
        @PathVariable String discrepancyId
    ) {
        PaymentDiscrepancyDetailInfo discrepancy = getPaymentDiscrepancyUseCase.get(
            actorUserId,
            PaymentDiscrepancyIdParser.parseRequired(discrepancyId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetPaymentDiscrepancyResponse.from(discrepancy)
        ).toResponseEntity();
    }
}
