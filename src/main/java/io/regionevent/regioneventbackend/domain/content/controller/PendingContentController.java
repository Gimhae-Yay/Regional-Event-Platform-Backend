package io.regionevent.regioneventbackend.domain.content.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.PendingContentsResponse;
import io.regionevent.regioneventbackend.domain.content.service.GetPendingContentsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.PendingContentListResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/contents")
public class PendingContentController {

    private static final String SUCCESS_MESSAGE = "담당 지역 승인 대기 콘텐츠 목록 조회에 성공했습니다.";

    private final GetPendingContentsUseCase getPendingContentsUseCase;

    public PendingContentController(GetPendingContentsUseCase getPendingContentsUseCase) {
        this.getPendingContentsUseCase = getPendingContentsUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PendingContentsResponse>> getPendingContents(
        Authentication authentication,
        @RequestParam(required = false) String status
    ) {
        PendingContentListResult result = getPendingContentsUseCase.get(
            toAuthenticatedUserId(authentication),
            status
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            PendingContentsResponse.from(result)
        ).toResponseEntity();
    }

    private Long toAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
