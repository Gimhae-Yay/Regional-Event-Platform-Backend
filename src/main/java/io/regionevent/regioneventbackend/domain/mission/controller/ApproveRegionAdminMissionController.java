package io.regionevent.regioneventbackend.domain.mission.controller;

import static io.regionevent.regioneventbackend.domain.mission.controller.MissionIdParser.toMissionId;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.mission.dto.ApproveRegionAdminMissionResponse;
import io.regionevent.regioneventbackend.domain.mission.service.ApproveRegionAdminMissionResult;
import io.regionevent.regioneventbackend.domain.mission.service.ApproveRegionAdminMissionUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/missions")
public class ApproveRegionAdminMissionController {

    private static final String APPROVAL_SUCCESS_MESSAGE = "\uBBF8\uC158 \uC2B9\uC778\uC5D0 \uC131\uACF5\uD588\uC2B5\uB2C8\uB2E4.";

    private final ApproveRegionAdminMissionUseCase approveRegionAdminMissionUseCase;

    public ApproveRegionAdminMissionController(ApproveRegionAdminMissionUseCase approveRegionAdminMissionUseCase) {
        this.approveRegionAdminMissionUseCase = approveRegionAdminMissionUseCase;
    }

    @PostMapping("/{missionId}/approve")
    public ResponseEntity<ApiResponse<ApproveRegionAdminMissionResponse>> approve(
        @AuthenticationPrincipal Long userId,
        @PathVariable String missionId,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        ApproveRegionAdminMissionResult result = approveRegionAdminMissionUseCase.approve(
            userId,
            toMissionId(missionId),
            UUID.fromString(requestId)
        );
        return ApiResponse
            .success(
                HttpStatus.OK,
                APPROVAL_SUCCESS_MESSAGE,
                ApproveRegionAdminMissionResponse.from(result)
            )
            .toResponseEntity();
    }
}
