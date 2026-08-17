package io.regionevent.regioneventbackend.domain.mission.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionProgress;
import io.regionevent.regioneventbackend.domain.mission.service.MissionParticipationDetailResult;

public record GetMyMissionParticipationResponse(
    String participationId,
    String missionId,
    MissionParticipationStatus status,
    MissionConditionType conditionType,
    int progressCount,
    int requiredCount,
    boolean rewardClaimed,
    Instant joinedAt,
    Instant completedAt,
    List<ProgressResponse> progresses
) {

    public static GetMyMissionParticipationResponse from(MissionParticipationDetailResult result) {
        MissionParticipation participation = result.participation();
        return new GetMyMissionParticipationResponse(
            participation.getMissionParticipationId().toString(),
            participation.getMission().getMissionId().toString(),
            participation.getStatus(),
            participation.getMission().getConditionType(),
            result.progressCount(),
            result.requiredCount(),
            result.rewardClaimed(),
            participation.getJoinedAt(),
            participation.getCompletedAt(),
            result.progresses().stream()
                .map(ProgressResponse::from)
                .toList()
        );
    }

    public record ProgressResponse(
        String visitId,
        String contentId,
        String contentTitle,
        Instant recordedAt
    ) {

        private static ProgressResponse from(MissionProgress progress) {
            return new ProgressResponse(
                progress.getVisit().getVisitId().toString(),
                progress.getContent().getContentId().toString(),
                progress.getContent().getTitle(),
                progress.getRecordedAt()
            );
        }
    }
}
