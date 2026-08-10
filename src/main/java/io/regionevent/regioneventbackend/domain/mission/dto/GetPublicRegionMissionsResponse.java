package io.regionevent.regioneventbackend.domain.mission.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.service.PublicRegionMissionListResult;

public record GetPublicRegionMissionsResponse(
    List<MissionResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

    public GetPublicRegionMissionsResponse {
        content = List.copyOf(content);
    }

    public static GetPublicRegionMissionsResponse from(PublicRegionMissionListResult result) {
        return new GetPublicRegionMissionsResponse(
            result.content().stream().map(MissionResponse::from).toList(),
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages()
        );
    }

    public record MissionResponse(
        String missionId,
        String regionId,
        MissionConditionType conditionType,
        Integer requiredVisitCount,
        int targetContentCount,
        OffsetDateTime endsAt,
        MissionParticipationStatus participationStatus
    ) {

        private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

        private static MissionResponse from(PublicRegionMissionListResult.Mission mission) {
            return new MissionResponse(
                mission.missionId().toString(),
                mission.regionId().toString(),
                mission.conditionType(),
                mission.requiredVisitCount(),
                mission.targetContentCount(),
                mission.endsAt().atZone(SEOUL_TIME_ZONE).toOffsetDateTime(),
                mission.participationStatus()
            );
        }
    }
}
