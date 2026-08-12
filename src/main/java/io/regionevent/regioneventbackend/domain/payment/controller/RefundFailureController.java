package io.regionevent.regioneventbackend.domain.payment.controller;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.payment.dto.GetRefundFailuresResponse;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.service.GetRefundFailuresUseCase;
import io.regionevent.regioneventbackend.domain.payment.service.RefundFailureListInfo;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/platform-admin/refund-failures")
public class RefundFailureController {

    private static final Set<String> ALLOWED_STATUSES = Set.of(
        "REQUESTED",
        "PROCESSING",
        "SUCCEEDED",
        "FAILED",
        "DISCREPANT"
    );
    private static final Set<RefundStatus> DEFAULT_STATUSES = EnumSet.of(
        RefundStatus.FAILED,
        RefundStatus.DISCREPANT
    );
    private static final String SUCCESS_MESSAGE = "환불 실패 목록 조회에 성공했습니다.";

    private final GetRefundFailuresUseCase getRefundFailuresUseCase;

    public RefundFailureController(GetRefundFailuresUseCase getRefundFailuresUseCase) {
        this.getRefundFailuresUseCase = getRefundFailuresUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GetRefundFailuresResponse>> getFailures(
        @AuthenticationPrincipal Long actorUserId,
        @RequestParam(required = false) String status
    ) {
        List<RefundFailureListInfo> refunds = getRefundFailuresUseCase.get(
            actorUserId,
            toStatuses(status)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetRefundFailuresResponse.from(refunds)
        ).toResponseEntity();
    }

    private Set<RefundStatus> toStatuses(String status) {
        if (status == null) {
            return DEFAULT_STATUSES;
        }
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return EnumSet.of(RefundStatus.valueOf(status));
    }
}
