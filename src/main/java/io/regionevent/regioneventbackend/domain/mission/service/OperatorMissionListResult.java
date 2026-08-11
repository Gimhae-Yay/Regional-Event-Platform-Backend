package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;

public record OperatorMissionListResult(
    List<MissionSummary> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

    public OperatorMissionListResult {
        content = List.copyOf(content);
    }

    public static OperatorMissionListResult from(Page<Mission> missions) {
        return new OperatorMissionListResult(
            missions.getContent().stream().map(MissionSummary::from).toList(),
            missions.getNumber(),
            missions.getSize(),
            missions.getTotalElements(),
            missions.getTotalPages()
        );
    }

    public record MissionSummary(
        Long missionId,
        MissionStatus status,
        MissionConditionType conditionType,
        Instant endsAt
    ) {

        private static MissionSummary from(Mission mission) {
            return new MissionSummary(
                mission.getMissionId(),
                mission.getStatus(),
                mission.getConditionType(),
                mission.getEndsAt()
            );
        }
    }
}
