package io.regionevent.regioneventbackend.domain.mission.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;

public interface MissionParticipationSummaryProjection {

    Long getParticipationId();

    Long getMissionId();

    String getTitle();

    MissionParticipationStatus getStatus();

    Long getProgressCount();

    Long getRequiredCount();

    boolean getRewardClaimed();

    Instant getJoinedAt();

    Instant getCompletedAt();
}
