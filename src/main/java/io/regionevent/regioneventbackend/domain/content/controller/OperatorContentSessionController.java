package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.CancelContentSessionRequest;
import io.regionevent.regioneventbackend.domain.content.dto.CancelContentSessionResponse;
import io.regionevent.regioneventbackend.domain.content.service.CancelContentSessionResult;
import io.regionevent.regioneventbackend.domain.content.service.CancelContentSessionUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/sessions")
public class OperatorContentSessionController {

    private static final String CANCEL_SUCCESS_MESSAGE = "회차 취소에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final CancelContentSessionUseCase cancelContentSessionUseCase;

    public OperatorContentSessionController(CancelContentSessionUseCase cancelContentSessionUseCase) {
        this.cancelContentSessionUseCase = cancelContentSessionUseCase;
    }

    @PostMapping("/{sessionId}/cancel")
    public ResponseEntity<ApiResponse<CancelContentSessionResponse>> cancelSession(
        Authentication authentication,
        @PathVariable String sessionId,
        @Valid @RequestBody CancelContentSessionRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        Long userId = (Long) authentication.getPrincipal();
        CancelContentSessionResult result = cancelContentSessionUseCase.cancel(
            userId,
            toSessionId(sessionId),
            request.cancellationReason(),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            CANCEL_SUCCESS_MESSAGE,
            CancelContentSessionResponse.from(result)
        ).toResponseEntity();
    }

    private Long toSessionId(String value) {
        Long parsedSessionId;
        try {
            parsedSessionId = Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return parsedSessionId;
    }
}
