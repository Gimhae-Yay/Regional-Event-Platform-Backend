package io.regionevent.regioneventbackend.domain.mission.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.mission.dto.CreateMissionParticipationResponse;
import io.regionevent.regioneventbackend.domain.mission.service.CreateMissionParticipationResult;
import io.regionevent.regioneventbackend.domain.mission.service.CreateMissionParticipationUseCase;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/missions")
public class MissionParticipationController {

    private static final String SUCCESS_MESSAGE = "미션 참여에 성공했습니다.";

    private final CreateMissionParticipationUseCase createMissionParticipationUseCase;

    public MissionParticipationController(CreateMissionParticipationUseCase createMissionParticipationUseCase) {
        this.createMissionParticipationUseCase = createMissionParticipationUseCase;
    }

    @PostMapping("/{missionId}/participations")
    public ResponseEntity<ApiResponse<CreateMissionParticipationResponse>> create(
        @AuthenticationPrincipal Long userId,
        @PathVariable String missionId
    ) {
        CreateMissionParticipationResult result = createMissionParticipationUseCase.create(
            userId,
            MissionIdParser.toMissionId(missionId)
        );
        return ApiResponse.success(
            HttpStatus.CREATED,
            SUCCESS_MESSAGE,
            CreateMissionParticipationResponse.from(result)
        ).toResponseEntity();
    }
}
