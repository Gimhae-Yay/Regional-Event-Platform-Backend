package io.regionevent.regioneventbackend.domain.mission.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.service.OperatorMissionListResult.MissionSummary;

public record OperatorMissionSummaryResponse(
    String missionId,
    MissionStatus status,
    MissionConditionType conditionType,
    OffsetDateTime endsAt
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public static OperatorMissionSummaryResponse from(MissionSummary mission) {
        return new OperatorMissionSummaryResponse(
            mission.missionId().toString(),
            mission.status(),
            mission.conditionType(),
            mission.endsAt().atZone(SEOUL_TIME_ZONE).toOffsetDateTime()
        );
    }
}
