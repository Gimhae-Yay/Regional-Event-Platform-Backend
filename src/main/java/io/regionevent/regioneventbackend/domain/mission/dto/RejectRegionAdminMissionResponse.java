package io.regionevent.regioneventbackend.domain.mission.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.service.RejectRegionAdminMissionResult;

public record RejectRegionAdminMissionResponse(
    String missionId,
    MissionStatus status,
    Instant rejectedAt
) {

    public static RejectRegionAdminMissionResponse from(RejectRegionAdminMissionResult result) {
        return new RejectRegionAdminMissionResponse(
            result.missionId().toString(),
            result.status(),
            result.rejectedAt()
        );
    }
}
