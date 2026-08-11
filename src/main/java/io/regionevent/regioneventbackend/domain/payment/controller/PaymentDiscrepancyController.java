package io.regionevent.regioneventbackend.domain.payment.controller;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.payment.dto.GetPaymentDiscrepanciesResponse;
import io.regionevent.regioneventbackend.domain.payment.service.GetPaymentDiscrepanciesUseCase;
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

    private final GetPaymentDiscrepanciesUseCase getPaymentDiscrepanciesUseCase;

    public PaymentDiscrepancyController(
        GetPaymentDiscrepanciesUseCase getPaymentDiscrepanciesUseCase
    ) {
        this.getPaymentDiscrepanciesUseCase = getPaymentDiscrepanciesUseCase;
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

    private String toStatus(String status) {
        String requestedStatus = status == null ? DEFAULT_STATUS : status;
        if (!ALLOWED_STATUSES.contains(requestedStatus)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return requestedStatus;
    }
}
