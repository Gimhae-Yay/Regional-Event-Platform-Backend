package io.regionevent.regioneventbackend.domain.reservation.controller;

import java.util.regex.Pattern;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.reservation.dto.GetMyReservationQrResponse;
import io.regionevent.regioneventbackend.domain.reservation.service.GetMyReservationQrUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.MyReservationQrResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/me/reservations")
public class MyReservationQrController {

    private static final String SUCCESS_MESSAGE = "예약 QR 조회에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final GetMyReservationQrUseCase getMyReservationQrUseCase;

    public MyReservationQrController(GetMyReservationQrUseCase getMyReservationQrUseCase) {
        this.getMyReservationQrUseCase = getMyReservationQrUseCase;
    }

    @GetMapping("/{reservationId}/qr")
    public ResponseEntity<ApiResponse<GetMyReservationQrResponse>> get(
        @AuthenticationPrincipal Long userId,
        @PathVariable String reservationId
    ) {
        MyReservationQrResult result = getMyReservationQrUseCase.get(userId, toReservationId(reservationId));
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(ApiResponse.success(HttpStatus.OK, SUCCESS_MESSAGE, GetMyReservationQrResponse.from(result)));
    }

    private Long toReservationId(String value) {
        Long parsedReservationId;
        try {
            parsedReservationId = Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return parsedReservationId;
    }
}
