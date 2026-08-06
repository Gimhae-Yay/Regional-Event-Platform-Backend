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

import io.regionevent.regioneventbackend.domain.content.dto.RejectSessionRevisionRequest;
import io.regionevent.regioneventbackend.domain.content.dto.RejectSessionRevisionResponse;
import io.regionevent.regioneventbackend.domain.content.service.RejectSessionRevisionResult;
import io.regionevent.regioneventbackend.domain.content.service.RejectSessionRevisionUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/session-revisions")
public class SessionRevisionRejectionController {

    private static final String SUCCESS_MESSAGE = "회차 수정 요청 반려에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final RejectSessionRevisionUseCase rejectSessionRevisionUseCase;

    public SessionRevisionRejectionController(RejectSessionRevisionUseCase rejectSessionRevisionUseCase) {
        this.rejectSessionRevisionUseCase = rejectSessionRevisionUseCase;
    }

    @PostMapping("/{revisionId}/reject")
    public ResponseEntity<ApiResponse<RejectSessionRevisionResponse>> reject(
        @AuthenticationPrincipal Long userId,
        @PathVariable String revisionId,
        @Valid @RequestBody RejectSessionRevisionRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        RejectSessionRevisionResult result = rejectSessionRevisionUseCase.reject(
            userId,
            toRevisionId(revisionId),
            request.reason(),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            RejectSessionRevisionResponse.from(result)
        ).toResponseEntity();
    }

    private Long toRevisionId(String value) {
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
        }
    }
}
