package io.regionevent.regioneventbackend.domain.mission.controller;

import static io.regionevent.regioneventbackend.domain.mission.controller.MissionIdParser.toMissionId;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.mission.dto.RegionAdminMissionDetailResponse;
import io.regionevent.regioneventbackend.domain.mission.service.GetRegionAdminMissionDetailUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/region-admin/missions")
public class RegionAdminMissionController {

    private static final String DETAIL_SUCCESS_MESSAGE = "지역 미션 상세 조회에 성공했습니다.";
    private final GetRegionAdminMissionDetailUseCase getRegionAdminMissionDetailUseCase;

    public RegionAdminMissionController(GetRegionAdminMissionDetailUseCase getRegionAdminMissionDetailUseCase) {
        this.getRegionAdminMissionDetailUseCase = getRegionAdminMissionDetailUseCase;
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

}
