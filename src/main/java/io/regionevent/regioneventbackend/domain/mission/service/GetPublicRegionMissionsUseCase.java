package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.region.service.RegionService;

@Service
public class GetPublicRegionMissionsUseCase {

    private final RegionService regionService;
    private final MissionService missionService;
    private final Clock clock;

    public GetPublicRegionMissionsUseCase(
        RegionService regionService,
        MissionService missionService,
        Clock clock
    ) {
        this.regionService = regionService;
        this.missionService = missionService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PublicRegionMissionListResult get(
        Long regionId,
        Long userId,
        int page,
        int size
    ) {
        regionService.findPublicRegion(regionId);
        Instant now = Instant.now(clock);
        return missionService.findPublicRegionMissions(regionId, userId, now, PageRequest.of(page, size));
    }
}
