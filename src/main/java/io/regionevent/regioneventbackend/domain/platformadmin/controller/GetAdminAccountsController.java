package io.regionevent.regioneventbackend.domain.platformadmin.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.platformadmin.dto.GetAdminAccountsResponse;
import io.regionevent.regioneventbackend.domain.platformadmin.service.AdminAccountListInfo;
import io.regionevent.regioneventbackend.domain.platformadmin.service.GetAdminAccountsUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/platform-admin/admin-accounts")
public class GetAdminAccountsController {

    private static final String SUCCESS_MESSAGE = "전체관리자 계정 목록 조회에 성공했습니다.";

    private final GetAdminAccountsUseCase getAdminAccountsUseCase;

    public GetAdminAccountsController(GetAdminAccountsUseCase getAdminAccountsUseCase) {
        this.getAdminAccountsUseCase = getAdminAccountsUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GetAdminAccountsResponse>> getAdminAccounts(
        @AuthenticationPrincipal Long actorUserId
    ) {
        List<AdminAccountListInfo> adminAccounts = getAdminAccountsUseCase.get(actorUserId);
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetAdminAccountsResponse.from(adminAccounts)
        ).toResponseEntity();
    }
}
