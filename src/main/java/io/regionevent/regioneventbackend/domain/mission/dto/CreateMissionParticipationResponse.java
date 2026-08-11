package io.regionevent.regioneventbackend.domain.mission.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.service.CreateMissionParticipationResult;

public record CreateMissionParticipationResponse(
    String participationId,
    String missionId,
    MissionParticipationStatus status,
    Instant joinedAt
) {

    public static CreateMissionParticipationResponse from(CreateMissionParticipationResult result) {
        return new CreateMissionParticipationResponse(
            result.participationId().toString(),
            result.missionId().toString(),
            result.status(),
            result.joinedAt()
        );
    }
}
