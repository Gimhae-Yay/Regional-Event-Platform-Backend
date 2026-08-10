package io.regionevent.regioneventbackend.domain.mission.service;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;

public record PublicMissionDetailResult(
    Mission mission,
    MissionParticipationSummary participation
) {
}
