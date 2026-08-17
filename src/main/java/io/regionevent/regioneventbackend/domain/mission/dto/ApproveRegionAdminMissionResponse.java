package io.regionevent.regioneventbackend.domain.mission.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.service.ApproveRegionAdminMissionResult;

public record ApproveRegionAdminMissionResponse(
    String missionId,
    MissionStatus status,
    Instant publishedAt
) {

    public static ApproveRegionAdminMissionResponse from(ApproveRegionAdminMissionResult result) {
        return new ApproveRegionAdminMissionResponse(
            result.missionId().toString(),
            result.status(),
            result.publishedAt()
        );
    }
}
