package io.regionevent.regioneventbackend.domain.payment.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.payment.dto.GetMyRefundResponse;
import io.regionevent.regioneventbackend.domain.payment.dto.GetMyRefundsResponse;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.service.GetMyRefundUseCase;
import io.regionevent.regioneventbackend.domain.payment.service.GetMyRefundsUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/me/refunds")
public class MyRefundController {

    private static final String SUCCESS_MESSAGE = "내 환불 목록 조회에 성공했습니다.";

    private static final String DETAIL_SUCCESS_MESSAGE = "내 환불 상세 조회에 성공했습니다.";

    private final GetMyRefundUseCase getMyRefundUseCase;
    private final GetMyRefundsUseCase getMyRefundsUseCase;

    public MyRefundController(
        GetMyRefundUseCase getMyRefundUseCase,
        GetMyRefundsUseCase getMyRefundsUseCase
    ) {
        this.getMyRefundUseCase = getMyRefundUseCase;
        this.getMyRefundsUseCase = getMyRefundsUseCase;
    }

    @GetMapping("/{refundId}")
    public ResponseEntity<ApiResponse<GetMyRefundResponse>> get(
        @AuthenticationPrincipal Long userId,
        @PathVariable String refundId
    ) {
        Long parsedRefundId = MyRefundIdParser.parseRequired(refundId);
        Refund refund = getMyRefundUseCase.find(userId, parsedRefundId);
        return ApiResponse.success(
            HttpStatus.OK,
            DETAIL_SUCCESS_MESSAGE,
            GetMyRefundResponse.from(refund)
        ).toResponseEntity();
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
