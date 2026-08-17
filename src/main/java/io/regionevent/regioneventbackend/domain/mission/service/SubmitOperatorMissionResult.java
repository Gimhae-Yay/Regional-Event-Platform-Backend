package io.regionevent.regioneventbackend.domain.mission.service;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;

public record SubmitOperatorMissionResult(
    Long missionId,
    MissionStatus status
) {

    public static SubmitOperatorMissionResult from(Mission mission) {
        return new SubmitOperatorMissionResult(mission.getMissionId(), mission.getStatus());
    }
}
