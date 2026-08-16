package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.RejectContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.dto.RejectContentWithdrawalResponse;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentWithdrawalResult;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentWithdrawalUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/content-withdrawal-requests")
public class ContentWithdrawalRejectionController {

    private static final String SUCCESS_MESSAGE = "전체 콘텐츠 철회 요청을 반려했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final RejectContentWithdrawalUseCase rejectContentWithdrawalUseCase;

    public ContentWithdrawalRejectionController(
        RejectContentWithdrawalUseCase rejectContentWithdrawalUseCase
    ) {
        this.rejectContentWithdrawalUseCase = rejectContentWithdrawalUseCase;
    }

    @PostMapping("/{withdrawalRequestId}/reject")
    public ResponseEntity<ApiResponse<RejectContentWithdrawalResponse>> reject(
        @AuthenticationPrincipal Long userId,
        @PathVariable String withdrawalRequestId,
        @Valid @RequestBody RejectContentWithdrawalRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        RejectContentWithdrawalResult result = rejectContentWithdrawalUseCase.reject(
            userId,
            toWithdrawalRequestId(withdrawalRequestId),
            request.reason(),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            RejectContentWithdrawalResponse.from(result)
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
