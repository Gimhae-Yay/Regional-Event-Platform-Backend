package io.regionevent.regioneventbackend.domain.mission.controller;

import static io.regionevent.regioneventbackend.domain.mission.controller.MissionIdParser.toMissionId;

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

import io.regionevent.regioneventbackend.domain.mission.dto.RejectRegionAdminMissionRequest;
import io.regionevent.regioneventbackend.domain.mission.dto.RejectRegionAdminMissionResponse;
import io.regionevent.regioneventbackend.domain.mission.service.RejectRegionAdminMissionResult;
import io.regionevent.regioneventbackend.domain.mission.service.RejectRegionAdminMissionUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/missions")
public class RejectRegionAdminMissionController {

    private static final String REJECTION_SUCCESS_MESSAGE = "미션 반려에 성공했습니다.";

    private final RejectRegionAdminMissionUseCase rejectRegionAdminMissionUseCase;

    public RejectRegionAdminMissionController(RejectRegionAdminMissionUseCase rejectRegionAdminMissionUseCase) {
        this.rejectRegionAdminMissionUseCase = rejectRegionAdminMissionUseCase;
    }

    @PostMapping("/{missionId}/reject")
    public ResponseEntity<ApiResponse<RejectRegionAdminMissionResponse>> reject(
        @AuthenticationPrincipal Long userId,
        @PathVariable String missionId,
        @Valid @RequestBody RejectRegionAdminMissionRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        RejectRegionAdminMissionResult result = rejectRegionAdminMissionUseCase.reject(
            userId,
            toMissionId(missionId),
            request.reasonCode(),
            UUID.fromString(requestId)
        );
        return ApiResponse
            .success(
                HttpStatus.OK,
                REJECTION_SUCCESS_MESSAGE,
                RejectRegionAdminMissionResponse.from(result)
            )
            .toResponseEntity();
    }
}
