package io.regionevent.regioneventbackend.domain.mission.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionTargetContent;

public record OperatorMissionDetailResponse(
    String missionId,
    String title,
    String regionId,
    MissionStatus status,
    MissionConditionType conditionType,
    Integer requiredVisitCount,
    List<TargetContentResponse> targetContents,
    String rewardCouponPolicyId,
    OffsetDateTime endsAt,
    Instant publishedAt,
    Instant endedAt
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public static OperatorMissionDetailResponse from(Mission mission) {
        return new OperatorMissionDetailResponse(
            mission.getMissionId().toString(),
            mission.getTitle(),
            mission.getRegion().getRegionId().toString(),
            mission.getStatus(),
            mission.getConditionType(),
            mission.getRequiredVisitCount(),
            mission.getTargetContents().stream()
                .sorted(Comparator.comparing(targetContent -> targetContent.getContent().getContentId()))
                .map(TargetContentResponse::from)
                .toList(),
            mission.getRewardCouponPolicy().getCouponPolicyId().toString(),
            mission.getEndsAt().atZone(SEOUL_TIME_ZONE).toOffsetDateTime(),
            mission.getPublishedAt(),
            mission.getEndedAt()
        );
    }

    public record TargetContentResponse(
        String contentId,
        String title
    ) {

        private static TargetContentResponse from(MissionTargetContent targetContent) {
            return new TargetContentResponse(
                targetContent.getContent().getContentId().toString(),
                targetContent.getContent().getTitle()
            );
        }
    }
}
