package io.regionevent.regioneventbackend.domain.mission.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.regionevent.regioneventbackend.domain.mission.dto.ClaimMissionRewardResponse;
import io.regionevent.regioneventbackend.domain.mission.service.ClaimMissionRewardUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/me/mission-participations")
public class ClaimMissionRewardController {

    private static final String SUCCESS_MESSAGE = "미션 보상 수령에 성공했습니다.";

    private final ClaimMissionRewardUseCase claimMissionRewardUseCase;

    public ClaimMissionRewardController(ClaimMissionRewardUseCase claimMissionRewardUseCase) {
        this.claimMissionRewardUseCase = claimMissionRewardUseCase;
    }

    @PostMapping("/{participationId}/rewards/claim")
    public ResponseEntity<ApiResponse<ClaimMissionRewardResponse>> claim(
        @AuthenticationPrincipal Long userId,
        @PathVariable String participationId,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        return ApiResponse.success(
            HttpStatus.CREATED,
            SUCCESS_MESSAGE,
            ClaimMissionRewardResponse.from(claimMissionRewardUseCase.claim(
                userId,
                MissionParticipationIdParser.parseRequired(participationId),
                UUID.fromString(requestId)
            ))
        ).toResponseEntity();
    }
}
