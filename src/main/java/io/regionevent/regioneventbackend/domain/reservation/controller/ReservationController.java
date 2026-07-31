package io.regionevent.regioneventbackend.domain.reservation.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.reservation.dto.ReservationConfirmationResponse;
import io.regionevent.regioneventbackend.domain.reservation.usecase.ReservationConfirmationUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@Validated
@RestController
@RequestMapping("/api/v1/reservation-holds")
public class ReservationController {

    private static final String RESERVATION_CONFIRMATION_SUCCESS_MESSAGE = "무료 예약 확정에 성공했습니다.";

    private final ReservationConfirmationUseCase reservationConfirmationUseCase;

    public ReservationController(ReservationConfirmationUseCase reservationConfirmationUseCase) {
        this.reservationConfirmationUseCase = reservationConfirmationUseCase;
    }

    @PostMapping("/{holdId}/confirm")
    public ResponseEntity<ApiResponse<ReservationConfirmationResponse>> confirm(
        @PathVariable @Positive Long holdId,
        @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
        Authentication authentication,
        HttpServletRequest request
    ) {
        ReservationConfirmationResponse response = reservationConfirmationUseCase.confirm(
            extractActorUserId(authentication),
            holdId,
            idempotencyKey,
            extractRequestId(request)
        );
        return ApiResponse.success(HttpStatus.CREATED, RESERVATION_CONFIRMATION_SUCCESS_MESSAGE, response)
            .toResponseEntity();
    }

    private static Long extractActorUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
    }

    private static String extractRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (!(requestId instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("requestId must be assigned before the reservation confirmation");
        }
        return value;
    }
}
