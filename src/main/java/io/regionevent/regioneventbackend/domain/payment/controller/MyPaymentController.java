package io.regionevent.regioneventbackend.domain.payment.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.payment.dto.GetMyPaymentResponse;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.service.GetMyPaymentUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/me/payments")
public class MyPaymentController {

    private static final String SUCCESS_MESSAGE = "내 결제 상태 조회에 성공했습니다.";

    private final GetMyPaymentUseCase getMyPaymentUseCase;

    public MyPaymentController(GetMyPaymentUseCase getMyPaymentUseCase) {
        this.getMyPaymentUseCase = getMyPaymentUseCase;
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<GetMyPaymentResponse>> get(
        @AuthenticationPrincipal Long userId,
        @PathVariable String paymentId
    ) {
        Long parsedPaymentId = MyPaymentIdParser.parseRequired(paymentId);
        Payment payment = getMyPaymentUseCase.find(userId, parsedPaymentId);
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetMyPaymentResponse.from(payment)
        ).toResponseEntity();
    }
}
