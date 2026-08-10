package io.regionevent.regioneventbackend.domain.mission.service;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;

public record CreateOperatorMissionResult(
    Long missionId,
    MissionStatus status
) {

    public static CreateOperatorMissionResult from(Mission mission) {
        return new CreateOperatorMissionResult(
            mission.getMissionId(),
            mission.getStatus()
        );
    }
}
