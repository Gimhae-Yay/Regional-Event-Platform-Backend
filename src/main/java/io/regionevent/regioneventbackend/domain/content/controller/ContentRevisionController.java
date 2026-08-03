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

import io.regionevent.regioneventbackend.domain.content.dto.RejectContentRevisionRequest;
import io.regionevent.regioneventbackend.domain.content.dto.RejectContentRevisionResponse;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentRevisionResult;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentRevisionUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/content-revisions")
public class ContentRevisionController {

    private static final String REJECT_SUCCESS_MESSAGE = "콘텐츠 수정본 반려에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final RejectContentRevisionUseCase rejectContentRevisionUseCase;

    public ContentRevisionController(RejectContentRevisionUseCase rejectContentRevisionUseCase) {
        this.rejectContentRevisionUseCase = rejectContentRevisionUseCase;
    }

    @PostMapping("/{revisionId}/reject")
    public ResponseEntity<ApiResponse<RejectContentRevisionResponse>> rejectContentRevision(
        Authentication authentication,
        @PathVariable String revisionId,
        @Valid @RequestBody RejectContentRevisionRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        Long userId = (Long) authentication.getPrincipal();
        RejectContentRevisionResult result = rejectContentRevisionUseCase.reject(
            userId,
            toRevisionId(revisionId),
            request.reason(),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            REJECT_SUCCESS_MESSAGE,
            RejectContentRevisionResponse.from(result)
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
