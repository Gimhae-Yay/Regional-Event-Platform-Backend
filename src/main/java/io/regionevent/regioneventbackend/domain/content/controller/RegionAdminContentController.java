package io.regionevent.regioneventbackend.domain.content.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.content.dto.RegionAdminContentsResponse;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.service.GetRegionAdminContentsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.RegionAdminContentListResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/contents")
public class RegionAdminContentController {

    private static final String PENDING_SUCCESS_MESSAGE = "담당 지역 승인 대기 콘텐츠 목록 조회에 성공했습니다.";
    private static final String APPROVED_SUCCESS_MESSAGE = "담당 지역 승인 완료 콘텐츠 목록 조회에 성공했습니다.";

    private final GetRegionAdminContentsUseCase getRegionAdminContentsUseCase;

    public RegionAdminContentController(GetRegionAdminContentsUseCase getRegionAdminContentsUseCase) {
        this.getRegionAdminContentsUseCase = getRegionAdminContentsUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<RegionAdminContentsResponse>> getRegionAdminContents(
        Authentication authentication,
        @RequestParam(required = false) String status
    ) {
        RegionAdminContentListResult result = getRegionAdminContentsUseCase.get(
            toAuthenticatedUserId(authentication),
            status
        );
        return ApiResponse.success(
            HttpStatus.OK,
            successMessage(result.status()),
            RegionAdminContentsResponse.from(result)
        ).toResponseEntity();
    }

    private String successMessage(ContentStatus status) {
        return switch (status) {
            case PENDING -> PENDING_SUCCESS_MESSAGE;
            case APPROVED -> APPROVED_SUCCESS_MESSAGE;
            default -> throw new IllegalStateException("unsupported content status: " + status);
        };
    }

    private Long toAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
