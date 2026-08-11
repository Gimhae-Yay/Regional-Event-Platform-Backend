package io.regionevent.regioneventbackend.domain.payment.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.payment.dto.GetMyRefundsResponse;
import io.regionevent.regioneventbackend.domain.payment.service.GetMyRefundsUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/me/refunds")
public class MyRefundController {

    private static final String SUCCESS_MESSAGE = "내 환불 목록 조회에 성공했습니다.";

    private final GetMyRefundsUseCase getMyRefundsUseCase;

    public MyRefundController(GetMyRefundsUseCase getMyRefundsUseCase) {
        this.getMyRefundsUseCase = getMyRefundsUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GetMyRefundsResponse>> getMyRefunds(
        @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetMyRefundsResponse.from(getMyRefundsUseCase.findAll(userId))
        ).toResponseEntity();
    }
}
