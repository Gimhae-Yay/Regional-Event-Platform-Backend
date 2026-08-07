package io.regionevent.regioneventbackend.domain.content.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.CreateContentRequest;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentResponse;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentRevisionRequest;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentRevisionResponse;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentSessionRequest;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentSessionResponse;
import io.regionevent.regioneventbackend.domain.content.service.CreateContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.CreateContentRevisionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.CreateContentSessionResult;
import io.regionevent.regioneventbackend.domain.content.service.CreateContentSessionUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/contents")
public class ContentController {

    private static final String CREATE_CONTENT_SUCCESS_MESSAGE = "콘텐츠 생성과 승인 요청에 성공했습니다.";

    private static final String CREATE_CONTENT_REVISION_SUCCESS_MESSAGE = "콘텐츠 수정본 생성과 승인 요청에 성공했습니다.";

    private static final String CREATE_CONTENT_SESSION_SUCCESS_MESSAGE = "콘텐츠 회차 생성에 성공했습니다.";

    private final CreateContentUseCase createContentUseCase;
    private final CreateContentRevisionUseCase createContentRevisionUseCase;
    private final CreateContentSessionUseCase createContentSessionUseCase;

    public ContentController(
        CreateContentUseCase createContentUseCase,
        CreateContentRevisionUseCase createContentRevisionUseCase,
        CreateContentSessionUseCase createContentSessionUseCase
    ) {
        this.createContentUseCase = createContentUseCase;
        this.createContentRevisionUseCase = createContentRevisionUseCase;
        this.createContentSessionUseCase = createContentSessionUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateContentResponse>> createContent(
        Authentication authentication,
        @Valid @RequestBody CreateContentRequest request
    ) {
        CreateContentResponse response = createContentUseCase.createContent(
            toAuthenticatedUserId(authentication),
            request
        );
        return ApiResponse
            .success(HttpStatus.CREATED, CREATE_CONTENT_SUCCESS_MESSAGE, response)
            .toResponseEntity();
    }

    @PostMapping("/{contentId}/revisions")
    public ResponseEntity<ApiResponse<CreateContentRevisionResponse>> createContentRevision(
        Authentication authentication,
        @PathVariable String contentId,
        @Valid @RequestBody CreateContentRevisionRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        CreateContentRevisionResponse response = createContentRevisionUseCase.createRevision(
            toAuthenticatedUserId(authentication),
            parsePositiveId(contentId),
            request,
            requestId
        );
        return ApiResponse
            .success(HttpStatus.CREATED, CREATE_CONTENT_REVISION_SUCCESS_MESSAGE, response)
            .toResponseEntity();
    }

    @PostMapping("/{contentId}/sessions")
    public ResponseEntity<ApiResponse<CreateContentSessionResponse>> createContentSession(
        Authentication authentication,
        @PathVariable String contentId,
        @Valid @RequestBody CreateContentSessionRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        CreateContentSessionResult result = createContentSessionUseCase.create(
            toAuthenticatedUserId(authentication),
            parseSessionCreateContentId(contentId),
            request,
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.CREATED,
            CREATE_CONTENT_SESSION_SUCCESS_MESSAGE,
            CreateContentSessionResponse.from(result)
        ).toResponseEntity();
    }

    private Long toAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }

    private Long parsePositiveId(String value) {
        return parsePositiveId(value, ErrorCode.INVALID_INPUT);
    }

    private Long parseSessionCreateContentId(String value) {
        return parsePositiveId(value, ErrorCode.INVALID_TYPE);
    }

    private Long parsePositiveId(String value, ErrorCode nonNumericErrorCode) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            Long parsedValue = Long.valueOf(value);
            if (!value.matches("[1-9]\\d*")) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            return parsedValue;
        } catch (NumberFormatException exception) {
            throw new BusinessException(nonNumericErrorCode, exception);
        }
    }
}
