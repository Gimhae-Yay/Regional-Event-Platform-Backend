package io.regionevent.regioneventbackend.domain.reservation.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.reservation.dto.ConfirmReservationResponse;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationConfirmationResult;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationConfirmationUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/reservation-holds")
public class ReservationHoldController {

    private static final String CONFIRM_RESERVATION_SUCCESS_MESSAGE = "무료 예약 확정에 성공했습니다.";

    private final ReservationConfirmationUseCase reservationConfirmationUseCase;

    public ReservationHoldController(ReservationConfirmationUseCase reservationConfirmationUseCase) {
        this.reservationConfirmationUseCase = reservationConfirmationUseCase;
    }

    @PostMapping("/{holdId}/confirm")
    public ResponseEntity<ApiResponse<ConfirmReservationResponse>> confirmReservation(
        Authentication authentication,
        @PathVariable Long holdId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        Long userId = (Long) authentication.getPrincipal();
        ReservationConfirmationResult result = reservationConfirmationUseCase.confirm(
            userId,
            holdId.toString(),
            idempotencyKey,
            UUID.fromString(requestId)
        );
        if (!result.isSuccessful()) {
            throw new BusinessException(result.errorCode());
        }
        return ApiResponse.success(
            HttpStatus.CREATED,
            CONFIRM_RESERVATION_SUCCESS_MESSAGE,
            result.response()
        ).toResponseEntity();
    }
}
