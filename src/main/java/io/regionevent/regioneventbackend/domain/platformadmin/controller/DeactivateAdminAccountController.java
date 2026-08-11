package io.regionevent.regioneventbackend.domain.platformadmin.controller;

import static io.regionevent.regioneventbackend.domain.platformadmin.controller.PlatformAdminAccountIdParser.toId;

import java.util.UUID;

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

import io.regionevent.regioneventbackend.domain.platformadmin.dto.DeactivateAdminAccountRequest;
import io.regionevent.regioneventbackend.domain.platformadmin.dto.DeactivateAdminAccountResponse;
import io.regionevent.regioneventbackend.domain.platformadmin.service.DeactivateAdminAccountResult;
import io.regionevent.regioneventbackend.domain.platformadmin.service.DeactivateAdminAccountUseCase;
import io.regionevent.regioneventbackend.domain.platformadmin.service.DeactivateAdminAccountUseCase.DeactivateAdminAccountCommand;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/platform-admin/admin-accounts")
public class DeactivateAdminAccountController {

    private static final String SUCCESS_MESSAGE = "전체관리자 계정 비활성화에 성공했습니다.";

    private final DeactivateAdminAccountUseCase deactivateAdminAccountUseCase;

    public DeactivateAdminAccountController(DeactivateAdminAccountUseCase deactivateAdminAccountUseCase) {
        this.deactivateAdminAccountUseCase = deactivateAdminAccountUseCase;
    }

    @PostMapping("/{userId}/deactivate")
    public ResponseEntity<ApiResponse<DeactivateAdminAccountResponse>> deactivateAdminAccount(
        @AuthenticationPrincipal Long actorUserId,
        @PathVariable String userId,
        @Valid @RequestBody DeactivateAdminAccountRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        DeactivateAdminAccountResult result = deactivateAdminAccountUseCase.deactivate(
            actorUserId,
            toId(userId),
            new DeactivateAdminAccountCommand(request.reasonCode(), request.evidenceReference()),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            DeactivateAdminAccountResponse.from(result)
        ).toResponseEntity();
    }
}
