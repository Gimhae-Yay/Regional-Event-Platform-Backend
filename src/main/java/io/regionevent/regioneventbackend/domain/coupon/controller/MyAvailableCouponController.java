package io.regionevent.regioneventbackend.domain.coupon.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.coupon.dto.GetMyAvailableCouponsResponse;
import io.regionevent.regioneventbackend.domain.coupon.service.GetMyAvailableCouponsUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/me/coupons")
public class MyAvailableCouponController {

    private static final String SUCCESS_MESSAGE = "사용 가능한 내 쿠폰 목록 조회에 성공했습니다.";

    private final GetMyAvailableCouponsUseCase getMyAvailableCouponsUseCase;

    public MyAvailableCouponController(GetMyAvailableCouponsUseCase getMyAvailableCouponsUseCase) {
        this.getMyAvailableCouponsUseCase = getMyAvailableCouponsUseCase;
    }

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<GetMyAvailableCouponsResponse>> getMyAvailableCoupons(
        @AuthenticationPrincipal Long userId,
        @RequestParam String holdId
    ) {
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetMyAvailableCouponsResponse.from(
                getMyAvailableCouponsUseCase.findAll(userId, MyAvailableCouponRequestHoldIdParser.parseRequired(holdId))
            )
        ).toResponseEntity();
    }
}
