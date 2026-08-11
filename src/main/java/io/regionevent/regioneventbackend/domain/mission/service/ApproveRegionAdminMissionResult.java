package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;

public record ApproveRegionAdminMissionResult(
    Long missionId,
    MissionStatus status,
    Instant publishedAt
) {

    public static ApproveRegionAdminMissionResult from(Mission mission) {
        return new ApproveRegionAdminMissionResult(
            mission.getMissionId(),
            mission.getStatus(),
            mission.getPublishedAt()
        );
    }
}
