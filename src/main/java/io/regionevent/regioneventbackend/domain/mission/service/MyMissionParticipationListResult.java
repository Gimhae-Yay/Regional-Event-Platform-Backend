package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;

public record MyMissionParticipationListResult(
    List<Participation> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

    public MyMissionParticipationListResult {
        content = List.copyOf(content);
    }

    public record Participation(
        Long participationId,
        Long missionId,
        MissionParticipationStatus status,
        int progressCount,
        int requiredCount,
        boolean rewardClaimed,
        Instant joinedAt,
        Instant completedAt
    ) {

        public static Participation from(MissionParticipationSummary summary) {
            return new Participation(
                summary.participationId(),
                summary.missionId(),
                summary.status(),
                summary.progressCount(),
                summary.requiredCount(),
                summary.rewardClaimed(),
                summary.joinedAt(),
                summary.completedAt()
            );
        }
    }
}
