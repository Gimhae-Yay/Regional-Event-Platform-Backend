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

import io.regionevent.regioneventbackend.domain.mission.dto.EndOperatorMissionRequest;
import io.regionevent.regioneventbackend.domain.mission.dto.EndOperatorMissionResponse;
import io.regionevent.regioneventbackend.domain.mission.service.EndOperatorMissionResult;
import io.regionevent.regioneventbackend.domain.mission.service.EndOperatorMissionUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/operator/missions")
public class EndOperatorMissionController {

    private static final String SUCCESS_MESSAGE = "미션 종료에 성공했습니다.";

    private final EndOperatorMissionUseCase endOperatorMissionUseCase;

    public EndOperatorMissionController(EndOperatorMissionUseCase endOperatorMissionUseCase) {
        this.endOperatorMissionUseCase = endOperatorMissionUseCase;
    }

    @PostMapping("/{missionId}/end")
    public ResponseEntity<ApiResponse<EndOperatorMissionResponse>> end(
        @AuthenticationPrincipal Long userId,
        @PathVariable String missionId,
        @Valid @RequestBody EndOperatorMissionRequest request,
        @RequestAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) String requestId
    ) {
        EndOperatorMissionResult result = endOperatorMissionUseCase.end(
            userId,
            toMissionId(missionId),
            request.reasonCode(),
            UUID.fromString(requestId)
        );
        return ApiResponse.success(
            HttpStatus.OK,
            SUCCESS_MESSAGE,
            EndOperatorMissionResponse.from(result)
        ).toResponseEntity();
    }
}
