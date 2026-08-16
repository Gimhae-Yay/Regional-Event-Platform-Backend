package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.ApproveContentWithdrawalResponse;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentWithdrawalResult;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentWithdrawalUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/content-withdrawal-requests")
public class ContentWithdrawalApprovalController {

    private static final String SUCCESS_MESSAGE = "전체 콘텐츠 철회 요청을 승인했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final ApproveContentWithdrawalUseCase approveContentWithdrawalUseCase;

    public ContentWithdrawalApprovalController(
        ApproveContentWithdrawalUseCase approveContentWithdrawalUseCase
    ) {
        this.approveContentWithdrawalUseCase = approveContentWithdrawalUseCase;
    }

    @PostMapping("/{withdrawalRequestId}/approve")
    public ResponseEntity<ApiResponse<ApproveContentWithdrawalResponse>> approve(
        @AuthenticationPrincipal Long userId,
        @PathVariable String withdrawalRequestId,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        ApproveContentWithdrawalResult result = approveContentWithdrawalUseCase.approve(
            userId,
            toWithdrawalRequestId(withdrawalRequestId),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            ApproveContentWithdrawalResponse.from(result)
        ).toResponseEntity();
    }

    private Long toWithdrawalRequestId(String value) {
        Long withdrawalRequestId;
        try {
            withdrawalRequestId = Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return withdrawalRequestId;
    }
}
