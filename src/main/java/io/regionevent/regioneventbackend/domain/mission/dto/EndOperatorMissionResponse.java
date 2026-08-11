package io.regionevent.regioneventbackend.domain.mission.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.service.EndOperatorMissionResult;

public record EndOperatorMissionResponse(
    String missionId,
    MissionStatus status,
    Instant endedAt
) {

    public static EndOperatorMissionResponse from(EndOperatorMissionResult result) {
        return new EndOperatorMissionResponse(
            result.missionId().toString(),
            result.status(),
            result.endedAt()
        );
    }
}
