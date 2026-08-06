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

import io.regionevent.regioneventbackend.domain.content.dto.CreateContentSessionRequest;
import io.regionevent.regioneventbackend.domain.content.dto.CreateSessionRevisionResponse;
import io.regionevent.regioneventbackend.domain.content.service.CreateSessionRevisionResult;
import io.regionevent.regioneventbackend.domain.content.service.CreateSessionRevisionUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/sessions")
public class SessionRevisionCreationController {

    private static final String CREATE_SESSION_REVISION_SUCCESS_MESSAGE = "콘텐츠 회차 수정 요청에 성공했습니다.";
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final CreateSessionRevisionUseCase createSessionRevisionUseCase;

    public SessionRevisionCreationController(CreateSessionRevisionUseCase createSessionRevisionUseCase) {
        this.createSessionRevisionUseCase = createSessionRevisionUseCase;
    }

    @PostMapping("/{sessionId}/change-requests")
    public ResponseEntity<ApiResponse<CreateSessionRevisionResponse>> createRevision(
        @AuthenticationPrincipal Long userId,
        @PathVariable String sessionId,
        @Valid @RequestBody CreateContentSessionRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        CreateSessionRevisionResult result = createSessionRevisionUseCase.create(
            userId,
            toSessionId(sessionId),
            request,
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.CREATED,
            CREATE_SESSION_REVISION_SUCCESS_MESSAGE,
            CreateSessionRevisionResponse.from(result)
        ).toResponseEntity();
    }

    private Long toSessionId(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            Long sessionId = Long.valueOf(value);
            if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            return sessionId;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
    }
}
