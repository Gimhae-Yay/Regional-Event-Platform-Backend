package io.regionevent.regioneventbackend.domain.user.controller;

import static io.regionevent.regioneventbackend.domain.user.controller.PlatformAdminUserRoleIdParser.toId;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.user.dto.ChangeRegionAdminRoleRequest;
import io.regionevent.regioneventbackend.domain.user.dto.ChangeRegionAdminRoleResponse;
import io.regionevent.regioneventbackend.domain.user.service.ChangeRegionAdminRoleUseCase;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminRoleChangeResult;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/platform-admin/users")
public class PlatformAdminUserRoleController {

    private static final String SUCCESS_MESSAGE = "지역관리자 역할 변경에 성공했습니다.";

    private final ChangeRegionAdminRoleUseCase changeRegionAdminRoleUseCase;

    public PlatformAdminUserRoleController(ChangeRegionAdminRoleUseCase changeRegionAdminRoleUseCase) {
        this.changeRegionAdminRoleUseCase = changeRegionAdminRoleUseCase;
    }

    @PatchMapping("/{userId}/role")
    public ResponseEntity<ApiResponse<ChangeRegionAdminRoleResponse>> changeRole(
        @AuthenticationPrincipal Long actorUserId,
        @PathVariable String userId,
        @Valid @RequestBody ChangeRegionAdminRoleRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        RegionAdminRoleChangeResult result = changeRegionAdminRoleUseCase.change(
            actorUserId,
            toId(userId),
            request.toRoleChange(),
            request.regionId() == null ? null : toId(request.regionId()),
            request.reasonCode(),
            request.evidenceReference(),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            ChangeRegionAdminRoleResponse.from(result)
        ).toResponseEntity();
    }
}
