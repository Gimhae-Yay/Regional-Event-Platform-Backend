package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionParticipationSummaryProjection;

public record MissionParticipationSummary(
    Long participationId,
    Long missionId,
    MissionParticipationStatus status,
    int progressCount,
    int requiredCount,
    boolean rewardClaimed,
    Instant joinedAt,
    Instant completedAt
) {

    public static MissionParticipationSummary from(MissionParticipationSummaryProjection projection) {
        return new MissionParticipationSummary(
            projection.getParticipationId(),
            projection.getMissionId(),
            projection.getStatus(),
            Math.toIntExact(projection.getProgressCount()),
            Math.toIntExact(projection.getRequiredCount()),
            projection.getRewardClaimed(),
            projection.getJoinedAt(),
            projection.getCompletedAt()
        );
    }
}
