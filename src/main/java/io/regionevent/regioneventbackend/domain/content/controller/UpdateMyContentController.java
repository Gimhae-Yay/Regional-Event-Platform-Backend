package io.regionevent.regioneventbackend.domain.content.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.UpdateMyContentRequest;
import io.regionevent.regioneventbackend.domain.content.dto.UpdateMyContentResponse;
import io.regionevent.regioneventbackend.domain.content.service.UpdateMyContentUseCase;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/contents")
public class UpdateMyContentController {

    private static final String UPDATE_MY_CONTENT_SUCCESS_MESSAGE = "내 콘텐츠 수정에 성공했습니다.";

    private final UpdateMyContentUseCase updateMyContentUseCase;

    public UpdateMyContentController(UpdateMyContentUseCase updateMyContentUseCase) {
        this.updateMyContentUseCase = updateMyContentUseCase;
    }

    @PutMapping("/{contentId}")
    public ResponseEntity<ApiResponse<UpdateMyContentResponse>> updateMyContent(
        Authentication authentication,
        @PathVariable Long contentId,
        @Valid @RequestBody UpdateMyContentRequest request
    ) {
        UpdateMyContentResponse response = updateMyContentUseCase.updateContent(
            toAuthenticatedUserId(authentication),
            contentId,
            request
        );
        return ApiResponse
            .success(HttpStatus.OK, UPDATE_MY_CONTENT_SUCCESS_MESSAGE, response)
            .toResponseEntity();
    }

    private Long toAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
