package io.regionevent.regioneventbackend.domain.mission.service;

import java.util.List;

import org.springframework.data.domain.Page;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;

public record RegionAdminMissionListResult(
    List<MissionSummary> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

    public RegionAdminMissionListResult {
        content = List.copyOf(content);
    }

    public static RegionAdminMissionListResult from(Page<Mission> missions) {
        return new RegionAdminMissionListResult(
            missions.getContent().stream().map(MissionSummary::from).toList(),
            missions.getNumber(),
            missions.getSize(),
            missions.getTotalElements(),
            missions.getTotalPages()
        );
    }

    public record MissionSummary(
        Long missionId,
        String title,
        MissionStatus status
    ) {

        private static MissionSummary from(Mission mission) {
            return new MissionSummary(mission.getMissionId(), mission.getTitle(), mission.getStatus());
        }
    }
}
