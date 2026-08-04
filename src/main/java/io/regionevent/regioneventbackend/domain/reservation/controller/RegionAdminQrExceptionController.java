package io.regionevent.regioneventbackend.domain.reservation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.reservation.dto.GetQrExceptionResponse;
import io.regionevent.regioneventbackend.domain.reservation.service.GetRegionAdminQrExceptionUseCase;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping({
    "/api/v1/region-admin/qr-exceptions",
    "/region-admin/qr-exceptions"
})
public class RegionAdminQrExceptionController {

    private static final String SUCCESS_MESSAGE = "QR 예외 상세 조회에 성공했습니다.";

    private final GetRegionAdminQrExceptionUseCase getRegionAdminQrExceptionUseCase;

    public RegionAdminQrExceptionController(
        GetRegionAdminQrExceptionUseCase getRegionAdminQrExceptionUseCase
    ) {
        this.getRegionAdminQrExceptionUseCase = getRegionAdminQrExceptionUseCase;
    }

    @GetMapping("/{exceptionId}")
    public ResponseEntity<ApiResponse<GetQrExceptionResponse>> get(
        Authentication authentication,
        @PathVariable String exceptionId
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetQrExceptionResponse.from(getRegionAdminQrExceptionUseCase.get(userId, parseExceptionId(exceptionId)))
        ).toResponseEntity();
    }

    private Long parseExceptionId(String exceptionId) {
        if (exceptionId == null || exceptionId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
        if (exceptionId.startsWith("-")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (!exceptionId.matches("^[0-9]+$")) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
        if (!exceptionId.matches("^[1-9][0-9]*$")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            return Long.valueOf(exceptionId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
    }
}
