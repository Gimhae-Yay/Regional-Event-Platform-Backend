package io.regionevent.regioneventbackend.domain.mission.repository;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;

public interface MissionUpdateSnapshot {

    Region getRegion();

    MissionStatus getStatus();

    Long getRewardCouponPolicyId();
}
