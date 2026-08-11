package io.regionevent.regioneventbackend.domain.payment.controller;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.payment.dto.GetPaymentDiscrepanciesResponse;
import io.regionevent.regioneventbackend.domain.payment.dto.GetPaymentDiscrepancyResponse;
import io.regionevent.regioneventbackend.domain.payment.service.GetPaymentDiscrepancyUseCase;
import io.regionevent.regioneventbackend.domain.payment.service.GetPaymentDiscrepanciesUseCase;
import io.regionevent.regioneventbackend.domain.payment.service.PaymentDiscrepancyDetailInfo;
import io.regionevent.regioneventbackend.domain.payment.service.PaymentDiscrepancyListInfo;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/platform-admin/payment-discrepancies")
public class PaymentDiscrepancyController {

    private static final String DEFAULT_STATUS = "OPEN";
    private static final Set<String> ALLOWED_STATUSES = Set.of(
        DEFAULT_STATUS,
        "RESOLVED_NO_ISSUE",
        "REFUND_REQUESTED"
    );
    private static final String SUCCESS_MESSAGE = "결제 불일치 목록 조회에 성공했습니다.";
    private static final String DETAIL_SUCCESS_MESSAGE = "결제 불일치 상세 조회에 성공했습니다.";

    private final GetPaymentDiscrepanciesUseCase getPaymentDiscrepanciesUseCase;
    private final GetPaymentDiscrepancyUseCase getPaymentDiscrepancyUseCase;

    public PaymentDiscrepancyController(
        GetPaymentDiscrepanciesUseCase getPaymentDiscrepanciesUseCase,
        GetPaymentDiscrepancyUseCase getPaymentDiscrepancyUseCase
    ) {
        this.getPaymentDiscrepanciesUseCase = getPaymentDiscrepanciesUseCase;
        this.getPaymentDiscrepancyUseCase = getPaymentDiscrepancyUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GetPaymentDiscrepanciesResponse>> getDiscrepancies(
        @AuthenticationPrincipal Long actorUserId,
        @RequestParam(required = false) String status
    ) {
        List<PaymentDiscrepancyListInfo> discrepancies = getPaymentDiscrepanciesUseCase.get(
            actorUserId,
            toStatus(status)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetPaymentDiscrepanciesResponse.from(discrepancies)
        ).toResponseEntity();
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
            DETAIL_SUCCESS_MESSAGE,
            GetPaymentDiscrepancyResponse.from(discrepancy)
        ).toResponseEntity();
    }

    private String toStatus(String status) {
        String requestedStatus = status == null ? DEFAULT_STATUS : status;
        if (!ALLOWED_STATUSES.contains(requestedStatus)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return requestedStatus;
    }
}
