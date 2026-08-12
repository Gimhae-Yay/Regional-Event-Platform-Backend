package io.regionevent.regioneventbackend.domain.payment.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.payment.dto.GetRefundFailureResponse;
import io.regionevent.regioneventbackend.domain.payment.service.GetRefundFailureUseCase;
import io.regionevent.regioneventbackend.domain.payment.service.RefundFailureDetailInfo;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/platform-admin/refund-failures")
public class RefundFailureDetailController {

    private static final String SUCCESS_MESSAGE = "환불 실패 상세 조회에 성공했습니다.";

    private final GetRefundFailureUseCase getRefundFailureUseCase;

    public RefundFailureDetailController(GetRefundFailureUseCase getRefundFailureUseCase) {
        this.getRefundFailureUseCase = getRefundFailureUseCase;
    }

    @GetMapping("/{refundId}")
    public ResponseEntity<ApiResponse<GetRefundFailureResponse>> get(
        @AuthenticationPrincipal Long actorUserId,
        @PathVariable String refundId
    ) {
        Long parsedRefundId = RefundFailureIdParser.parseRequired(refundId);
        RefundFailureDetailInfo detail = getRefundFailureUseCase.get(actorUserId, parsedRefundId);
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetRefundFailureResponse.from(detail)
        ).toResponseEntity();
    }
}
