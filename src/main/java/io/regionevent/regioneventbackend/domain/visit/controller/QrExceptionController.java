package io.regionevent.regioneventbackend.domain.visit.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.visit.dto.GetQrExceptionsResponse;
import io.regionevent.regioneventbackend.domain.visit.service.GetQrExceptionsUseCase;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
public class QrExceptionController {

    private static final String SUCCESS_MESSAGE = "QR 예외 목록 조회에 성공했습니다.";
    private static final int DEFAULT_SIZE = 20;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    private final GetQrExceptionsUseCase getQrExceptionsUseCase;

    public QrExceptionController(GetQrExceptionsUseCase getQrExceptionsUseCase) {
        this.getQrExceptionsUseCase = getQrExceptionsUseCase;
    }

    @GetMapping({"/api/v1/region-admin/qr-exceptions", "/region-admin/qr-exceptions"})
    public ResponseEntity<ApiResponse<GetQrExceptionsResponse>> getQrExceptions(
        @AuthenticationPrincipal Long userId,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "20") Integer size
    ) {
        int validatedSize = validateSize(size);
        GetQrExceptionsResponse response = getQrExceptionsUseCase.get(userId, cursor, validatedSize);
        return ApiResponse.success(HttpStatus.OK, SUCCESS_MESSAGE, response).toResponseEntity();
    }

    private int validateSize(Integer size) {
        int value = size == null ? DEFAULT_SIZE : size;
        if (value < MIN_SIZE || value > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return value;
    }
}
