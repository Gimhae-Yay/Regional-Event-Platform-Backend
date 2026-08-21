package io.regionevent.regioneventbackend.domain.stampbook.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.stampbook.dto.OperatorStampbookDetailResponse;
import io.regionevent.regioneventbackend.domain.stampbook.service.GetOperatorStampbookDetailUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/stampbooks")
public class OperatorStampbookDetailController {

    private static final Logger log = LoggerFactory.getLogger(OperatorStampbookDetailController.class);
    private static final String SUCCESS_MESSAGE = "운영자 스탬프북 상세 조회에 성공했습니다.";
    private static final String SUCCESS_RESULT_CODE = "SUCCESS";

    private final GetOperatorStampbookDetailUseCase getOperatorStampbookDetailUseCase;

    public OperatorStampbookDetailController(
        GetOperatorStampbookDetailUseCase getOperatorStampbookDetailUseCase
    ) {
        this.getOperatorStampbookDetailUseCase = getOperatorStampbookDetailUseCase;
    }

    @GetMapping("/{stampbookId}")
    public ResponseEntity<ApiResponse<OperatorStampbookDetailResponse>> getDetail(
        @AuthenticationPrincipal Long userId,
        @PathVariable String stampbookId,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        Long parsedStampbookId = null;
        try {
            parsedStampbookId = OperatorStampbookIdParser.parseRequired(stampbookId);
            OperatorStampbookDetailResponse response = getOperatorStampbookDetailUseCase.get(
                userId,
                parsedStampbookId
            );
            logResult(requestId, userId, parsedStampbookId, SUCCESS_RESULT_CODE);
            return ApiResponse.success(HttpStatus.OK, SUCCESS_MESSAGE, response).toResponseEntity();
        } catch (BusinessException exception) {
            logResult(requestId, userId, parsedStampbookId, exception.getErrorCode().code());
            throw exception;
        }
    }

    private void logResult(
        String requestId,
        Long userId,
        Long stampbookId,
        String resultCode
    ) {
        log.info(
            "Operator stampbook detail read. requestId={}, operatorId={}, stampbookId={}, resultCode={}",
            requestId,
            userId,
            stampbookId,
            resultCode
        );
    }
}
