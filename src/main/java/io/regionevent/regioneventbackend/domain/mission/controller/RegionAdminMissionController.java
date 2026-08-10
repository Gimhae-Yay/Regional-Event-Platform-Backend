package io.regionevent.regioneventbackend.domain.mission.controller;

import static io.regionevent.regioneventbackend.domain.mission.controller.MissionIdParser.toMissionId;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.mission.dto.ApproveRegionAdminMissionResponse;
import io.regionevent.regioneventbackend.domain.mission.dto.RegionAdminMissionDetailResponse;
import io.regionevent.regioneventbackend.domain.mission.service.ApproveRegionAdminMissionResult;
import io.regionevent.regioneventbackend.domain.mission.service.ApproveRegionAdminMissionUseCase;
import io.regionevent.regioneventbackend.domain.mission.service.GetRegionAdminMissionDetailUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/missions")
public class RegionAdminMissionController {

    private static final String DETAIL_SUCCESS_MESSAGE = "지역 미션 상세 조회에 성공했습니다.";
    private static final String APPROVAL_SUCCESS_MESSAGE = "미션 승인에 성공했습니다.";

    private final GetRegionAdminMissionDetailUseCase getRegionAdminMissionDetailUseCase;
    private final ApproveRegionAdminMissionUseCase approveRegionAdminMissionUseCase;

    public RegionAdminMissionController(
        GetRegionAdminMissionDetailUseCase getRegionAdminMissionDetailUseCase,
        ApproveRegionAdminMissionUseCase approveRegionAdminMissionUseCase
    ) {
        this.getRegionAdminMissionDetailUseCase = getRegionAdminMissionDetailUseCase;
        this.approveRegionAdminMissionUseCase = approveRegionAdminMissionUseCase;
    }

    @GetMapping("/{missionId}")
    public ResponseEntity<ApiResponse<RegionAdminMissionDetailResponse>> getDetail(
        @AuthenticationPrincipal Long userId,
        @PathVariable String missionId
    ) {
        RegionAdminMissionDetailResponse response = getRegionAdminMissionDetailUseCase.get(
            userId,
            toMissionId(missionId)
        );
        return ApiResponse
            .success(HttpStatus.OK, DETAIL_SUCCESS_MESSAGE, response)
            .toResponseEntity();
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
