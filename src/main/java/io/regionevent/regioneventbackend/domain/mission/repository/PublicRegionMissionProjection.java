package io.regionevent.regioneventbackend.domain.mission.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;

public interface PublicRegionMissionProjection {

    Long getMissionId();

    Long getRegionId();

    String getTitle();

    MissionConditionType getConditionType();

    Integer getRequiredVisitCount();

    Long getTargetContentCount();

    Instant getEndsAt();

    MissionParticipationStatus getParticipationStatus();
}
