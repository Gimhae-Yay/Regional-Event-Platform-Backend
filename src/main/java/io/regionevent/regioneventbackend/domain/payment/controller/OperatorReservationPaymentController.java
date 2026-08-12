package io.regionevent.regioneventbackend.domain.payment.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.payment.dto.GetOperatorReservationPaymentResponse;
import io.regionevent.regioneventbackend.domain.payment.service.GetOperatorReservationPaymentUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/reservations")
public class OperatorReservationPaymentController {

    private static final String SUCCESS_MESSAGE = "담당 예약 결제·환불 상태 조회에 성공했습니다.";

    private final GetOperatorReservationPaymentUseCase getOperatorReservationPaymentUseCase;

    public OperatorReservationPaymentController(
        GetOperatorReservationPaymentUseCase getOperatorReservationPaymentUseCase
    ) {
        this.getOperatorReservationPaymentUseCase = getOperatorReservationPaymentUseCase;
    }

    @GetMapping("/{reservationId}/payment")
    public ResponseEntity<ApiResponse<GetOperatorReservationPaymentResponse>> get(
        @AuthenticationPrincipal Long userId,
        @PathVariable String reservationId
    ) {
        GetOperatorReservationPaymentResponse response = GetOperatorReservationPaymentResponse.from(
            getOperatorReservationPaymentUseCase.get(
                userId,
                OperatorReservationPaymentIdParser.parseRequired(reservationId)
            )
        );
        return ApiResponse.success(HttpStatus.OK, SUCCESS_MESSAGE, response).toResponseEntity();
    }
}
