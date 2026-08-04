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

import io.regionevent.regioneventbackend.domain.content.dto.SuspendContentRequest;
import io.regionevent.regioneventbackend.domain.content.dto.SuspendContentResponse;
import io.regionevent.regioneventbackend.domain.content.service.SuspendContentResult;
import io.regionevent.regioneventbackend.domain.content.service.SuspendContentUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/contents")
public class SuspendContentController {

    private static final String SUCCESS_MESSAGE = "콘텐츠 운영 중단에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final SuspendContentUseCase suspendContentUseCase;

    public SuspendContentController(SuspendContentUseCase suspendContentUseCase) {
        this.suspendContentUseCase = suspendContentUseCase;
    }

    @PostMapping("/{contentId}/suspend")
    public ResponseEntity<ApiResponse<SuspendContentResponse>> suspendContent(
        Authentication authentication,
        @PathVariable String contentId,
        @Valid @RequestBody SuspendContentRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        Long userId = (Long) authentication.getPrincipal();
        SuspendContentResult result = suspendContentUseCase.suspend(
            userId,
            toContentId(contentId),
            request.reason(),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            SuspendContentResponse.from(result)
        ).toResponseEntity();
    }

    private Long toContentId(String value) {
        Long contentId;
        try {
            contentId = Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return contentId;
    }
}
