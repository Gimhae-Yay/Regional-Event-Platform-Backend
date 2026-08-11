package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;

public record CreateMissionParticipationResult(
    Long participationId,
    Long missionId,
    MissionParticipationStatus status,
    Instant joinedAt
) {

    public static CreateMissionParticipationResult from(MissionParticipation participation) {
        return new CreateMissionParticipationResult(
            participation.getMissionParticipationId(),
            participation.getMission().getMissionId(),
            participation.getStatus(),
            participation.getJoinedAt()
        );
    }
}
