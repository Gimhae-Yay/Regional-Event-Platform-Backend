package io.regionevent.regioneventbackend.domain.mission.controller;

import static io.regionevent.regioneventbackend.domain.mission.controller.MissionIdParser.toMissionId;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.mission.dto.GetPublicMissionResponse;
import io.regionevent.regioneventbackend.domain.mission.service.GetPublicMissionUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/missions")
public class PublicMissionController {

    private static final String SUCCESS_MESSAGE = "공개 미션 상세 조회에 성공했습니다.";

    private final GetPublicMissionUseCase getPublicMissionUseCase;

    public PublicMissionController(GetPublicMissionUseCase getPublicMissionUseCase) {
        this.getPublicMissionUseCase = getPublicMissionUseCase;
    }

    @GetMapping("/{missionId}")
    public ResponseEntity<ApiResponse<GetPublicMissionResponse>> getPublicMission(
        @AuthenticationPrincipal Long userId,
        @PathVariable String missionId
    ) {
        GetPublicMissionResponse response = GetPublicMissionResponse.from(
            getPublicMissionUseCase.get(toMissionId(missionId), userId)
        );
        return ApiResponse.success(HttpStatus.OK, SUCCESS_MESSAGE, response).toResponseEntity();
    }
}
