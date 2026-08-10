package io.regionevent.regioneventbackend.domain.mission.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.service.MyMissionParticipationListResult;

public record MyMissionParticipationResponse(
    String participationId,
    String missionId,
    MissionParticipationStatus status,
    int progressCount,
    int requiredCount,
    boolean rewardClaimed,
    Instant joinedAt,
    Instant completedAt
) {

    public static MyMissionParticipationResponse from(
        MyMissionParticipationListResult.Participation participation
    ) {
        return new MyMissionParticipationResponse(
            participation.participationId().toString(),
            participation.missionId().toString(),
            participation.status(),
            participation.progressCount(),
            participation.requiredCount(),
            participation.rewardClaimed(),
            participation.joinedAt(),
            participation.completedAt()
        );
    }
}
