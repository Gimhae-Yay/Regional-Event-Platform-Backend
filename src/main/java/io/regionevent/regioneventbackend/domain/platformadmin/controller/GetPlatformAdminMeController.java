package io.regionevent.regioneventbackend.domain.platformadmin.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.platformadmin.dto.GetPlatformAdminMeResponse;
import io.regionevent.regioneventbackend.domain.platformadmin.service.GetPlatformAdminMeUseCase;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.response.ApiResponse;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenAuthority;

@RestController
@RequestMapping("/api/v1/platform-admin")
public class GetPlatformAdminMeController {

    private static final String SUCCESS_MESSAGE = "전체관리자 본인 권한 조회에 성공했습니다.";

    private final GetPlatformAdminMeUseCase getPlatformAdminMeUseCase;

    public GetPlatformAdminMeController(GetPlatformAdminMeUseCase getPlatformAdminMeUseCase) {
        this.getPlatformAdminMeUseCase = getPlatformAdminMeUseCase;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<GetPlatformAdminMeResponse>> getPlatformAdminMe(
        @AuthenticationPrincipal Long actorUserId,
        Authentication authentication
    ) {
        Long userId = getPlatformAdminMeUseCase.get(actorUserId);
        PlatformAdminGrade grade = toGrade(authentication);
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetPlatformAdminMeResponse.from(userId, grade)
        ).toResponseEntity();
    }

    private PlatformAdminGrade toGrade(Authentication authentication) {
        if (hasAuthority(authentication, AccessTokenAuthority.SUPER_ADMIN)) {
            return PlatformAdminGrade.SUPER_ADMIN;
        }
        if (hasAuthority(authentication, AccessTokenAuthority.PLATFORM_ADMIN)) {
            return PlatformAdminGrade.PLATFORM_ADMIN;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private boolean hasAuthority(Authentication authentication, AccessTokenAuthority authority) {
        return authentication.getAuthorities().stream()
            .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(authority.claimValue()));
    }
}
