package io.regionevent.regioneventbackend.domain.mission.dto;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.service.CreateOperatorMissionResult;

public record CreateOperatorMissionResponse(
    String missionId,
    MissionStatus status
) {

    public static CreateOperatorMissionResponse from(CreateOperatorMissionResult result) {
        return new CreateOperatorMissionResponse(
            result.missionId().toString(),
            result.status()
        );
    }
}
