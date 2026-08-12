package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;

public record RejectRegionAdminMissionResult(
    Long missionId,
    MissionStatus status,
    Instant rejectedAt
) {
}
