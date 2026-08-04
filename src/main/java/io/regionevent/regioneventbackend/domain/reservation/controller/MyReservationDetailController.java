package io.regionevent.regioneventbackend.domain.reservation.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.reservation.dto.GetMyReservationResponse;
import io.regionevent.regioneventbackend.domain.reservation.service.GetMyReservationUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationReadResult;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/me/reservations")
public class MyReservationDetailController {

    private static final Logger log = LoggerFactory.getLogger(MyReservationDetailController.class);
    private static final String SUCCESS_MESSAGE = "예약 상세 조회에 성공했습니다.";
    private static final String SUCCESS_RESULT_CODE = "SUCCESS";

    private final GetMyReservationUseCase getMyReservationUseCase;

    public MyReservationDetailController(GetMyReservationUseCase getMyReservationUseCase) {
        this.getMyReservationUseCase = getMyReservationUseCase;
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<GetMyReservationResponse>> get(
        @AuthenticationPrincipal Long userId,
        @PathVariable String reservationId,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        Long parsedReservationId = null;
        try {
            parsedReservationId = MyReservationDetailRequestIdParser.parseRequired(reservationId);
            ReservationReadResult result = getMyReservationUseCase.find(userId, parsedReservationId);
            logResult(
                requestId,
                result,
                SUCCESS_RESULT_CODE
            );
            return ApiResponse.success(
                HttpStatus.OK,
                SUCCESS_MESSAGE,
                GetMyReservationResponse.from(result)
            ).toResponseEntity();
        } catch (BusinessException exception) {
            logResult(requestId, parsedReservationId, null, null, exception.getErrorCode().code());
            throw exception;
        } catch (RuntimeException exception) {
            logResult(requestId, parsedReservationId, null, null, ErrorCode.INTERNAL_SERVER_ERROR.code());
            throw exception;
        }
    }

    private void logResult(
        String requestId,
        ReservationReadResult result,
        String resultCode
    ) {
        logResult(
            requestId,
            result.snapshot().reservation().reservationId(),
            result.snapshot().session().sessionId(),
            result.checkIn().visitId(),
            resultCode
        );
    }

    private void logResult(
        String requestId,
        Long reservationId,
        Long sessionId,
        Long visitId,
        String resultCode
    ) {
        log.info(
            "Reservation detail read. requestId={}, reservationId={}, sessionId={}, visitId={}, resultCode={}",
            requestId,
            reservationId,
            sessionId,
            visitId,
            resultCode
        );
    }
}
