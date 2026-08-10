package io.regionevent.regioneventbackend.domain.coupon.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.coupon.dto.GetMyCouponsResponse;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.GetMyCouponsUseCase;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/me/coupons")
public class MyCouponController {

    private static final String SUCCESS_MESSAGE = "내 쿠폰 목록 조회에 성공했습니다.";

    private final GetMyCouponsUseCase getMyCouponsUseCase;

    public MyCouponController(GetMyCouponsUseCase getMyCouponsUseCase) {
        this.getMyCouponsUseCase = getMyCouponsUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GetMyCouponsResponse>> getMyCoupons(
        @AuthenticationPrincipal Long userId,
        @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetMyCouponsResponse.from(getMyCouponsUseCase.findAll(userId, toCouponStatus(status)))
        ).toResponseEntity();
    }

    private CouponStatus toCouponStatus(String value) {
        if (value == null) {
            return null;
        }
        try {
            return CouponStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
        }
    }
}
