package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;

public record EndOperatorMissionResult(
    Long missionId,
    MissionStatus status,
    Instant endedAt
) {

    public static EndOperatorMissionResult from(Mission mission) {
        return new EndOperatorMissionResult(
            mission.getMissionId(),
            mission.getStatus(),
            mission.getEndedAt()
        );
    }
}
