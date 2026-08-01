package io.regionevent.regioneventbackend.domain.content.controller;

import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.GetSessionReservationInfoResponse;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;
import io.regionevent.regioneventbackend.domain.content.service.PublicSessionReservationInfo;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/sessions")
public class ContentSessionController {

    private static final String GET_RESERVATION_INFO_SUCCESS_MESSAGE = "회차 예약 정보 조회에 성공했습니다.";

    private final ContentSessionService contentSessionService;

    public ContentSessionController(ContentSessionService contentSessionService) {
        this.contentSessionService = contentSessionService;
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<GetSessionReservationInfoResponse>> getReservationInfo(
        @PathVariable @Positive Long sessionId
    ) {
        PublicSessionReservationInfo reservationInfo = contentSessionService
            .findPublicScheduledReservationInfo(sessionId);
        GetSessionReservationInfoResponse response = GetSessionReservationInfoResponse.from(reservationInfo);
        return ApiResponse.success(HttpStatus.OK, GET_RESERVATION_INFO_SUCCESS_MESSAGE, response)
            .toResponseEntity();
    }
}
