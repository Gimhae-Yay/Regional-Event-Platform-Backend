package io.regionevent.regioneventbackend.domain.mission.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionTargetContent;
import io.regionevent.regioneventbackend.domain.mission.service.MissionParticipationSummary;
import io.regionevent.regioneventbackend.domain.mission.service.PublicMissionDetailResult;

public record GetPublicMissionResponse(
    String missionId,
    String regionId,
    MissionConditionType conditionType,
    Integer requiredVisitCount,
    List<TargetContentResponse> targetContents,
    String rewardCouponPolicyId,
    OffsetDateTime endsAt,
    ParticipationResponse participation
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public GetPublicMissionResponse {
        targetContents = List.copyOf(targetContents);
    }

    public static GetPublicMissionResponse from(PublicMissionDetailResult result) {
        Mission mission = result.mission();
        return new GetPublicMissionResponse(
            mission.getMissionId().toString(),
            mission.getRegion().getRegionId().toString(),
            mission.getConditionType(),
            mission.getRequiredVisitCount(),
            mission.getTargetContents().stream()
                .sorted(Comparator.comparing(targetContent -> targetContent.getContent().getContentId()))
                .map(TargetContentResponse::from)
                .toList(),
            mission.getRewardCouponPolicy().getCouponPolicyId().toString(),
            mission.getEndsAt().atZone(SEOUL_TIME_ZONE).toOffsetDateTime(),
            ParticipationResponse.from(result.participation())
        );
    }

    public record TargetContentResponse(String contentId, String title) {

        private static TargetContentResponse from(MissionTargetContent targetContent) {
            return new TargetContentResponse(
                targetContent.getContent().getContentId().toString(),
                targetContent.getContent().getTitle()
            );
        }
    }

    public record ParticipationResponse(
        String participationId,
        MissionParticipationStatus status,
        int progressCount,
        int requiredCount,
        boolean rewardClaimed
    ) {

        private static ParticipationResponse from(MissionParticipationSummary participation) {
            if (participation == null) {
                return null;
            }
            return new ParticipationResponse(
                participation.participationId().toString(),
                participation.status(),
                participation.progressCount(),
                participation.requiredCount(),
                participation.rewardClaimed()
            );
        }
    }
}
