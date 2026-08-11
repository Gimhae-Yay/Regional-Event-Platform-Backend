package io.regionevent.regioneventbackend.domain.mission.service;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;

public record UpdateOperatorMissionResult(
    Long missionId,
    MissionStatus status
) {

    public static UpdateOperatorMissionResult from(Mission mission) {
        return new UpdateOperatorMissionResult(mission.getMissionId(), mission.getStatus());
    }
}
