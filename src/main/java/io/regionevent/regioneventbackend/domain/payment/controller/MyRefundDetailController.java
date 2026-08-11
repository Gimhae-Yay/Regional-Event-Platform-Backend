package io.regionevent.regioneventbackend.domain.payment.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.payment.dto.GetMyRefundResponse;
import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.service.GetMyRefundUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/me/refunds")
public class MyRefundDetailController {

    private static final String SUCCESS_MESSAGE = "내 환불 상세 조회에 성공했습니다.";

    private final GetMyRefundUseCase getMyRefundUseCase;

    public MyRefundDetailController(GetMyRefundUseCase getMyRefundUseCase) {
        this.getMyRefundUseCase = getMyRefundUseCase;
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
            SUCCESS_MESSAGE,
            GetMyRefundResponse.from(refund)
        ).toResponseEntity();
    }
}
