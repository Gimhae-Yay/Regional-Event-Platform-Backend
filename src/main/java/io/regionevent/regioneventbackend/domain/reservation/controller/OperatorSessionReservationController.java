package io.regionevent.regioneventbackend.domain.reservation.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.reservation.dto.GetSessionReservationsResponse;
import io.regionevent.regioneventbackend.domain.reservation.service.GetSessionReservationsUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.SessionReservationListResult;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/contents")
public class OperatorSessionReservationController {

    private static final Logger log = LoggerFactory.getLogger(OperatorSessionReservationController.class);
    private static final String SUCCESS_MESSAGE = "회차별 예약자 목록 조회에 성공했습니다.";
    private static final String SUCCESS_RESULT_CODE = "SUCCESS";
    private static final int FAILURE_RESULT_COUNT = 0;
    private final GetSessionReservationsUseCase getSessionReservationsUseCase;

    public OperatorSessionReservationController(
        GetSessionReservationsUseCase getSessionReservationsUseCase
    ) {
        this.getSessionReservationsUseCase = getSessionReservationsUseCase;
    }

    @GetMapping("/{contentId}/reservations")
    public ResponseEntity<ApiResponse<GetSessionReservationsResponse>> getSessionReservations(
        Authentication authentication,
        @PathVariable String contentId,
        @RequestParam(required = false) String sessionId,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        Long parsedContentId = null;
        Long parsedSessionId = null;
        try {
            parsedContentId = OperatorSessionReservationRequestIdParser.parseRequired(contentId);
            parsedSessionId = OperatorSessionReservationRequestIdParser.parseRequired(sessionId);
            SessionReservationListResult result = getSessionReservationsUseCase.find(
                (Long) authentication.getPrincipal(),
                parsedContentId,
                parsedSessionId
            );
            logResult(
                requestId,
                parsedContentId,
                parsedSessionId,
                result.reservations().size(),
                SUCCESS_RESULT_CODE
            );
            return ApiResponse.success(
                HttpStatus.OK,
                SUCCESS_MESSAGE,
                GetSessionReservationsResponse.from(result)
            ).toResponseEntity();
        } catch (BusinessException exception) {
            logResult(
                requestId,
                parsedContentId,
                parsedSessionId,
                FAILURE_RESULT_COUNT,
                exception.getErrorCode().code()
            );
            throw exception;
        } catch (RuntimeException exception) {
            logResult(
                requestId,
                parsedContentId,
                parsedSessionId,
                FAILURE_RESULT_COUNT,
                ErrorCode.INTERNAL_SERVER_ERROR.code()
            );
            throw exception;
        }
    }

    private void logResult(
        String requestId,
        Long contentId,
        Long sessionId,
        int resultCount,
        String resultCode
    ) {
        log.info(
            "Session reservation list read. requestId={}, contentId={}, sessionId={}, resultCount={}, resultCode={}",
            requestId,
            contentId,
            sessionId,
            resultCount,
            resultCode
        );
    }

}
