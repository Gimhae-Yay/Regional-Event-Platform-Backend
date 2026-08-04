package io.regionevent.regioneventbackend.domain.reservation.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.reservation.dto.SearchOperatorReservationByNumberResponse;
import io.regionevent.regioneventbackend.domain.reservation.service.OperatorReservationSearchResult;
import io.regionevent.regioneventbackend.domain.reservation.service.SearchOperatorReservationByNumberUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/reservations")
public class OperatorReservationController {

    private static final String SUCCESS_MESSAGE = "예약번호 보조 조회에 성공했습니다.";

    private final SearchOperatorReservationByNumberUseCase searchOperatorReservationByNumberUseCase;

    public OperatorReservationController(
        SearchOperatorReservationByNumberUseCase searchOperatorReservationByNumberUseCase
    ) {
        this.searchOperatorReservationByNumberUseCase = searchOperatorReservationByNumberUseCase;
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<SearchOperatorReservationByNumberResponse>> search(
        Authentication authentication,
        @RequestParam String reservationNo,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        Long userId = (Long) authentication.getPrincipal();
        OperatorReservationSearchResult result = searchOperatorReservationByNumberUseCase.search(
            userId,
            reservationNo,
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            SearchOperatorReservationByNumberResponse.from(result)
        ).toResponseEntity();
    }
}
