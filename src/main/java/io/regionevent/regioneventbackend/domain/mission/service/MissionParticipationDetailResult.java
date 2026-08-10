package io.regionevent.regioneventbackend.domain.mission.service;

import java.util.List;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionProgress;

public record MissionParticipationDetailResult(
    MissionParticipation participation,
    List<MissionProgress> progresses,
    int progressCount,
    int requiredCount,
    boolean rewardClaimed
) {
}
