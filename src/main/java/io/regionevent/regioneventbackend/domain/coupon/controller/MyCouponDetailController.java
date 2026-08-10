package io.regionevent.regioneventbackend.domain.coupon.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.coupon.dto.GetMyCouponResponse;
import io.regionevent.regioneventbackend.domain.coupon.service.GetMyCouponUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/me/coupons")
public class MyCouponDetailController {

    private static final String SUCCESS_MESSAGE = "내 쿠폰 상세 조회에 성공했습니다.";

    private final GetMyCouponUseCase getMyCouponUseCase;

    public MyCouponDetailController(GetMyCouponUseCase getMyCouponUseCase) {
        this.getMyCouponUseCase = getMyCouponUseCase;
    }

    @GetMapping("/{couponId}")
    public ResponseEntity<ApiResponse<GetMyCouponResponse>> get(
        @AuthenticationPrincipal Long userId,
        @PathVariable String couponId
    ) {
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetMyCouponResponse.from(getMyCouponUseCase.find(
                userId,
                MyCouponDetailRequestIdParser.parseRequired(couponId)
            ))
        ).toResponseEntity();
    }
}
