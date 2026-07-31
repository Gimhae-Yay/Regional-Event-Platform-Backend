package io.regionevent.regioneventbackend.domain.reservation.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.reservation.dto.CreateReservationHoldRequest;
import io.regionevent.regioneventbackend.domain.reservation.dto.CreateReservationHoldResponse;
import io.regionevent.regioneventbackend.domain.reservation.service.CreateReservationHoldUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private static final String CREATE_HOLD_SUCCESS_MESSAGE = "예약 대기 및 정원 홀드 생성에 성공했습니다.";

    private final CreateReservationHoldUseCase createReservationHoldUseCase;

    public ReservationController(CreateReservationHoldUseCase createReservationHoldUseCase) {
        this.createReservationHoldUseCase = createReservationHoldUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateReservationHoldResponse>> createReservationHold(
        Authentication authentication,
        @Valid @RequestBody CreateReservationHoldRequest request
    ) {
        Long userId = (Long) authentication.getPrincipal();
        CreateReservationHoldResponse response = createReservationHoldUseCase.create(userId, request);
        return ApiResponse.success(HttpStatus.CREATED, CREATE_HOLD_SUCCESS_MESSAGE, response).toResponseEntity();
    }
}
