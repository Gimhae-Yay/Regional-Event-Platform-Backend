package io.regionevent.regioneventbackend.domain.payment.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.payment.dto.CreatePaymentRequest;
import io.regionevent.regioneventbackend.domain.payment.dto.CreatePaymentResponse;
import io.regionevent.regioneventbackend.domain.payment.service.CreatePaymentUseCase;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/me/reservation-holds")
public class PaymentController {

    private static final String SUCCESS_MESSAGE = "유료 예약 결제 생성에 성공했습니다.";

    private final CreatePaymentUseCase createPaymentUseCase;

    public PaymentController(CreatePaymentUseCase createPaymentUseCase) {
        this.createPaymentUseCase = createPaymentUseCase;
    }

    @PostMapping("/{holdId}/payments")
    public ResponseEntity<ApiResponse<CreatePaymentResponse>> create(
        Authentication authentication,
        @PathVariable String holdId,
        @RequestBody(required = false) CreatePaymentRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        validateCouponIdType(request);
        Long userId = (Long) authentication.getPrincipal();
        CreatePaymentResponse response = createPaymentUseCase.create(
            userId,
            holdId,
            request,
            idempotencyKey
        );
        return ApiResponse.success(HttpStatus.CREATED, SUCCESS_MESSAGE, response).toResponseEntity();
    }

    private void validateCouponIdType(CreatePaymentRequest request) {
        if (request != null
            && request.couponId() != null
            && !request.couponId().isNull()
            && !request.couponId().isString()) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
    }
}
