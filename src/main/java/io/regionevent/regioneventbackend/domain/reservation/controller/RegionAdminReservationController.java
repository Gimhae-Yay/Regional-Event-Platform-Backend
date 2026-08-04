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

import io.regionevent.regioneventbackend.domain.reservation.dto.SearchRegionAdminReservationByNumberResponse;
import io.regionevent.regioneventbackend.domain.reservation.service.RegionAdminReservationSearchResult;
import io.regionevent.regioneventbackend.domain.reservation.service.SearchRegionAdminReservationByNumberUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/reservations")
public class RegionAdminReservationController {

    private static final String SUCCESS_MESSAGE = "예약번호 보조 조회에 성공했습니다.";

    private final SearchRegionAdminReservationByNumberUseCase searchRegionAdminReservationByNumberUseCase;

    public RegionAdminReservationController(
        SearchRegionAdminReservationByNumberUseCase searchRegionAdminReservationByNumberUseCase
    ) {
        this.searchRegionAdminReservationByNumberUseCase = searchRegionAdminReservationByNumberUseCase;
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<SearchRegionAdminReservationByNumberResponse>> search(
        Authentication authentication,
        @RequestParam(required = false) String reservationNo,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        Long userId = (Long) authentication.getPrincipal();
        RegionAdminReservationSearchResult result = searchRegionAdminReservationByNumberUseCase.search(
            userId,
            reservationNo,
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            SearchRegionAdminReservationByNumberResponse.from(result)
        ).toResponseEntity();
    }
}
