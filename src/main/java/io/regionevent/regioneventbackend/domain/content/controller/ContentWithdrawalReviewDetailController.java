package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.ContentWithdrawalReviewDetailResponse;
import io.regionevent.regioneventbackend.domain.content.service.ContentWithdrawalReviewDetailResult;
import io.regionevent.regioneventbackend.domain.content.service.GetContentWithdrawalReviewDetailUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/content-withdrawal-requests")
public class ContentWithdrawalReviewDetailController {

    private static final Logger log = LoggerFactory.getLogger(
        ContentWithdrawalReviewDetailController.class
    );
    private static final String SUCCESS_MESSAGE = "전체 콘텐츠 철회 요청 상세 조회에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final GetContentWithdrawalReviewDetailUseCase getContentWithdrawalReviewDetailUseCase;

    public ContentWithdrawalReviewDetailController(
        GetContentWithdrawalReviewDetailUseCase getContentWithdrawalReviewDetailUseCase
    ) {
        this.getContentWithdrawalReviewDetailUseCase = getContentWithdrawalReviewDetailUseCase;
    }

    @GetMapping("/{withdrawalRequestId}")
    public ResponseEntity<ApiResponse<ContentWithdrawalReviewDetailResponse>> getReviewDetail(
        @AuthenticationPrincipal Long userId,
        @PathVariable String withdrawalRequestId
    ) {
        ContentWithdrawalReviewDetailResult result = getContentWithdrawalReviewDetailUseCase.get(
            userId,
            toWithdrawalRequestId(withdrawalRequestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            ContentWithdrawalReviewDetailResponse.from(result)
        ).toResponseEntity();
    }

    private Long toWithdrawalRequestId(String value) {
        Long withdrawalRequestId;
        try {
            withdrawalRequestId = Long.valueOf(value);
        } catch (NumberFormatException exception) {
            logValidationFailure(ErrorCode.INVALID_TYPE);
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            logValidationFailure(ErrorCode.INVALID_INPUT);
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return withdrawalRequestId;
    }

    private void logValidationFailure(ErrorCode errorCode) {
        log.info(
            "Content withdrawal review detail queried. requestId={}, regionId={}, withdrawalRequestId={}, resultCode={}",
            RequestIdFilter.currentRequestId(),
            null,
            null,
            errorCode.code()
        );
    }
}
