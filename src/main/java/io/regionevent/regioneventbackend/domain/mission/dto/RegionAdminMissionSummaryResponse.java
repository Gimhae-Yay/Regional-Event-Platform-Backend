package io.regionevent.regioneventbackend.domain.mission.dto;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.service.RegionAdminMissionListResult.MissionSummary;

public record RegionAdminMissionSummaryResponse(
    String missionId,
    MissionStatus status
) {

    public static RegionAdminMissionSummaryResponse from(MissionSummary mission) {
        return new RegionAdminMissionSummaryResponse(mission.missionId().toString(), mission.status());
    }
}
