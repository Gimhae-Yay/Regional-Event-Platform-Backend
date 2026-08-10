package io.regionevent.regioneventbackend.domain.mission.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.mission.dto.GetMyMissionParticipationResponse;
import io.regionevent.regioneventbackend.domain.mission.service.GetMyMissionParticipationUseCase;
import io.regionevent.regioneventbackend.domain.mission.service.MissionParticipationDetailResult;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/me/mission-participations")
public class MyMissionParticipationDetailController {

    private static final String SUCCESS_MESSAGE = "내 미션 참여 상세 조회에 성공했습니다.";

    private final GetMyMissionParticipationUseCase getMyMissionParticipationUseCase;

    public MyMissionParticipationDetailController(
        GetMyMissionParticipationUseCase getMyMissionParticipationUseCase
    ) {
        this.getMyMissionParticipationUseCase = getMyMissionParticipationUseCase;
    }

    @GetMapping("/{participationId}")
    public ResponseEntity<ApiResponse<GetMyMissionParticipationResponse>> get(
        @AuthenticationPrincipal Long userId,
        @PathVariable String participationId
    ) {
        MissionParticipationDetailResult result = getMyMissionParticipationUseCase.get(
            userId,
            MissionParticipationIdParser.parseRequired(participationId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            GetMyMissionParticipationResponse.from(result)
        ).toResponseEntity();
    }
}
