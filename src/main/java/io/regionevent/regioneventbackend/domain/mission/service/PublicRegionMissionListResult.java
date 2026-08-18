package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;

public record PublicRegionMissionListResult(
    List<Mission> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

    public PublicRegionMissionListResult {
        content = List.copyOf(content);
    }

    public record Mission(
        Long missionId,
        Long regionId,
        String title,
        MissionConditionType conditionType,
        Integer requiredVisitCount,
        int targetContentCount,
        Instant endsAt,
        MissionParticipationStatus participationStatus
    ) {
    }
}
