package io.regionevent.regioneventbackend.domain.content.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.CreateContentRequest;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentResponse;
import io.regionevent.regioneventbackend.domain.content.service.CreateContentUseCase;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/contents")
public class ContentController {

    private static final String CREATE_CONTENT_SUCCESS_MESSAGE = "콘텐츠 생성과 승인 요청에 성공했습니다.";

    private final CreateContentUseCase createContentUseCase;

    public ContentController(CreateContentUseCase createContentUseCase) {
        this.createContentUseCase = createContentUseCase;
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

    private Long toAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
