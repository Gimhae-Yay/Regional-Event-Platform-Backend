package io.regionevent.regioneventbackend.domain.coupon.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.coupon.dto.GetMyCouponUsageHistoryResponse;
import io.regionevent.regioneventbackend.domain.coupon.service.GetMyCouponUsageHistoryUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/me/coupons")
public class MyCouponUsageHistoryController {

    private static final String SUCCESS_MESSAGE = "내 쿠폰 사용 이력 조회에 성공했습니다.";

    private final GetMyCouponUsageHistoryUseCase getMyCouponUsageHistoryUseCase;

    public MyCouponUsageHistoryController(
        GetMyCouponUsageHistoryUseCase getMyCouponUsageHistoryUseCase
    ) {
        this.getMyCouponUsageHistoryUseCase = getMyCouponUsageHistoryUseCase;
    }

    @GetMapping("/{couponId}/usage-history")
    public ResponseEntity<ApiResponse<GetMyCouponUsageHistoryResponse>> get(
        @AuthenticationPrincipal Long userId,
        @PathVariable String couponId
    ) {
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetMyCouponUsageHistoryResponse.from(getMyCouponUsageHistoryUseCase.find(
                userId,
                MyCouponDetailRequestIdParser.parseRequired(couponId)
            ))
        ).toResponseEntity();
    }
}
