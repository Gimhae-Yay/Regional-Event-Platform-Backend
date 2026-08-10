package io.regionevent.regioneventbackend.domain.user.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.user.dto.GetPlatformAdminUsersResponse;
import io.regionevent.regioneventbackend.domain.user.service.GetPlatformAdminUsersUseCase;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminUserListInfo;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/platform-admin/users")
public class PlatformAdminUserController {

    private static final String SUCCESS_MESSAGE = "사용자 목록 조회에 성공했습니다.";

    private final GetPlatformAdminUsersUseCase getPlatformAdminUsersUseCase;

    public PlatformAdminUserController(GetPlatformAdminUsersUseCase getPlatformAdminUsersUseCase) {
        this.getPlatformAdminUsersUseCase = getPlatformAdminUsersUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GetPlatformAdminUsersResponse>> getUsers(
        @AuthenticationPrincipal Long actorUserId
    ) {
        List<PlatformAdminUserListInfo> users = getPlatformAdminUsersUseCase.get(actorUserId);
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetPlatformAdminUsersResponse.from(users)
        ).toResponseEntity();
    }
}
