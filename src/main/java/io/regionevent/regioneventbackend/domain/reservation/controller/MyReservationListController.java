package io.regionevent.regioneventbackend.domain.reservation.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.reservation.dto.GetMyReservationsResponse;
import io.regionevent.regioneventbackend.domain.reservation.service.GetMyReservationsUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationReadResult;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/me/reservations")
public class MyReservationListController {

    private static final Logger log = LoggerFactory.getLogger(MyReservationListController.class);
    private static final String SUCCESS_MESSAGE = "내 예약 목록 조회에 성공했습니다.";
    private static final String SUCCESS_RESULT_CODE = "SUCCESS";
    private static final int FAILURE_RESULT_COUNT = 0;

    private final GetMyReservationsUseCase getMyReservationsUseCase;

    public MyReservationListController(GetMyReservationsUseCase getMyReservationsUseCase) {
        this.getMyReservationsUseCase = getMyReservationsUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GetMyReservationsResponse>> getAll(
        @AuthenticationPrincipal Long userId,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        try {
            List<ReservationReadResult> results = getMyReservationsUseCase.findAll(userId);
            logResult(requestId, results.size(), SUCCESS_RESULT_CODE);
            return ApiResponse.success(
                HttpStatus.OK,
                SUCCESS_MESSAGE,
                GetMyReservationsResponse.from(results)
            ).toResponseEntity();
        } catch (BusinessException exception) {
            logResult(requestId, FAILURE_RESULT_COUNT, exception.getErrorCode().code());
            throw exception;
        } catch (RuntimeException exception) {
            logResult(requestId, FAILURE_RESULT_COUNT, ErrorCode.INTERNAL_SERVER_ERROR.code());
            throw exception;
        }
    }

    private void logResult(
        String requestId,
        int resultCount,
        String resultCode
    ) {
        log.info(
            "My reservation list read. requestId={}, resultCount={}, resultCode={}",
            requestId,
            resultCount,
            resultCode
        );
    }
}
