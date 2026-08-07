package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.SubmitContentResponse;
import io.regionevent.regioneventbackend.domain.content.service.SubmitContentResult;
import io.regionevent.regioneventbackend.domain.content.service.SubmitContentUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/contents")
public class SubmitContentController {

    private static final String SUCCESS_MESSAGE = "콘텐츠 승인 재요청에 성공했습니다.";

    private final SubmitContentUseCase submitContentUseCase;

    public SubmitContentController(SubmitContentUseCase submitContentUseCase) {
        this.submitContentUseCase = submitContentUseCase;
    }

    @PostMapping("/{contentId}/submit")
    public ResponseEntity<ApiResponse<SubmitContentResponse>> submitContent(
        Authentication authentication,
        @PathVariable String contentId,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        SubmitContentResult result = submitContentUseCase.submit(
            toAuthenticatedUserId(authentication),
            parsePositiveId(contentId),
            UUID.fromString(requestId)
        );
        return ApiResponse
            .success(HttpStatus.OK, SUCCESS_MESSAGE, SubmitContentResponse.from(result))
            .toResponseEntity();
    }

    private Long toAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }

    private Long parsePositiveId(String value) {
        if (value == null || !value.matches("[1-9]\\d*")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
