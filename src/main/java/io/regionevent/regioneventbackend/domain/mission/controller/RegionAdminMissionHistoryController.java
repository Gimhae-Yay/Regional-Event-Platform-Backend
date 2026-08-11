package io.regionevent.regioneventbackend.domain.mission.controller;

import static io.regionevent.regioneventbackend.domain.mission.controller.MissionIdParser.toMissionId;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.mission.dto.RegionAdminMissionHistoryResponse;
import io.regionevent.regioneventbackend.domain.mission.service.GetRegionAdminMissionHistoryUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/missions")
public class RegionAdminMissionHistoryController {

    private static final String SUCCESS_MESSAGE = "미션 이력 조회에 성공했습니다.";

    private final GetRegionAdminMissionHistoryUseCase getRegionAdminMissionHistoryUseCase;

    public RegionAdminMissionHistoryController(
        GetRegionAdminMissionHistoryUseCase getRegionAdminMissionHistoryUseCase
    ) {
        this.getRegionAdminMissionHistoryUseCase = getRegionAdminMissionHistoryUseCase;
    }

    @GetMapping("/{missionId}/history")
    public ResponseEntity<ApiResponse<RegionAdminMissionHistoryResponse>> getHistory(
        @AuthenticationPrincipal Long userId,
        @PathVariable String missionId
    ) {
        RegionAdminMissionHistoryResponse response = getRegionAdminMissionHistoryUseCase.get(
            userId,
            toMissionId(missionId)
        );
        return ApiResponse
            .success(HttpStatus.OK, SUCCESS_MESSAGE, response)
            .toResponseEntity();
    }
}
