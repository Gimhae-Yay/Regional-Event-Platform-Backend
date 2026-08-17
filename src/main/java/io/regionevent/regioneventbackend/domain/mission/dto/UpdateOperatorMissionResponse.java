package io.regionevent.regioneventbackend.domain.mission.dto;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.service.UpdateOperatorMissionResult;

public record UpdateOperatorMissionResponse(
    String missionId,
    MissionStatus status
) {

    public static UpdateOperatorMissionResponse from(UpdateOperatorMissionResult result) {
        return new UpdateOperatorMissionResponse(result.missionId().toString(), result.status());
    }
}
