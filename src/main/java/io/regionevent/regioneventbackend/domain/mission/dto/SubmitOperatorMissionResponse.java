package io.regionevent.regioneventbackend.domain.mission.dto;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.service.SubmitOperatorMissionResult;

public record SubmitOperatorMissionResponse(
    String missionId,
    MissionStatus status
) {

    public static SubmitOperatorMissionResponse from(SubmitOperatorMissionResult result) {
        return new SubmitOperatorMissionResponse(result.missionId().toString(), result.status());
    }
}
