package io.regionevent.regioneventbackend.domain.mission.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;

@Service
public class GetRegionAdminMissionsUseCase {

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final MissionService missionService;

    public GetRegionAdminMissionsUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        MissionService missionService
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.missionService = missionService;
    }

    @Transactional(readOnly = true)
    public RegionAdminMissionListResult get(
        Long userId,
        MissionStatus status,
        int page,
        int size
    ) {
        Long regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(userId);
        return RegionAdminMissionListResult.from(missionService.findRegionMissions(
            regionId,
            status,
            PageRequest.of(page, size)
        ));
    }
}
