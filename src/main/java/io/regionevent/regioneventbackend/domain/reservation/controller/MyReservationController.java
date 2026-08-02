package io.regionevent.regioneventbackend.domain.reservation.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.reservation.dto.CancelReservationResponse;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationCancellationUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/me/reservations")
public class MyReservationController {

    private static final String CANCEL_RESERVATION_SUCCESS_MESSAGE = "예약 취소에 성공했습니다.";

    private final ReservationCancellationUseCase reservationCancellationUseCase;

    public MyReservationController(ReservationCancellationUseCase reservationCancellationUseCase) {
        this.reservationCancellationUseCase = reservationCancellationUseCase;
    }

    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<ApiResponse<CancelReservationResponse>> cancelReservation(
        Authentication authentication,
        @PathVariable Long reservationId,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        Long userId = (Long) authentication.getPrincipal();
        CancelReservationResponse response = reservationCancellationUseCase.cancel(
            userId,
            reservationId,
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            CANCEL_RESERVATION_SUCCESS_MESSAGE,
            response
        ).toResponseEntity();
    }
}
