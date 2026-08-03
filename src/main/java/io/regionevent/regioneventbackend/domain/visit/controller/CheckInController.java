package io.regionevent.regioneventbackend.domain.visit.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.visit.dto.CheckInResponse;
import io.regionevent.regioneventbackend.domain.visit.dto.ManualCheckInRequest;
import io.regionevent.regioneventbackend.domain.visit.dto.QrCheckInRequest;
import io.regionevent.regioneventbackend.domain.visit.service.CheckInResult;
import io.regionevent.regioneventbackend.domain.visit.service.CheckInUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/check-ins")
public class CheckInController {

    private static final String QR_CHECK_IN_SUCCESS_MESSAGE = "QR 체크인에 성공했습니다.";
    private static final String MANUAL_CHECK_IN_SUCCESS_MESSAGE = "예약번호 보조 체크인에 성공했습니다.";

    private final CheckInUseCase checkInUseCase;

    public CheckInController(CheckInUseCase checkInUseCase) {
        this.checkInUseCase = checkInUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CheckInResponse>> checkInByQr(
        Authentication authentication,
        @Valid @RequestBody QrCheckInRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        Long userId = (Long) authentication.getPrincipal();
        CheckInResult result = checkInUseCase.checkInByQr(
            userId,
            request,
            idempotencyKey,
            UUID.fromString(requestId)
        );
        if (!result.isSuccessful()) {
            throw new BusinessException(result.errorCode());
        }
        return ApiResponse.success(
            HttpStatus.OK,
            QR_CHECK_IN_SUCCESS_MESSAGE,
            result.response()
        ).toResponseEntity();
    }

    @PostMapping("/manual")
    public ResponseEntity<ApiResponse<CheckInResponse>> checkInManually(
        Authentication authentication,
        @Valid @RequestBody ManualCheckInRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        Long userId = (Long) authentication.getPrincipal();
        CheckInResult result = checkInUseCase.checkInManually(
            userId,
            request,
            idempotencyKey,
            UUID.fromString(requestId)
        );
        if (!result.isSuccessful()) {
            throw new BusinessException(result.errorCode());
        }
        return ApiResponse.success(
            HttpStatus.OK,
            MANUAL_CHECK_IN_SUCCESS_MESSAGE,
            result.response()
        ).toResponseEntity();
    }
}
