package io.regionevent.regioneventbackend.domain.mission.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.mission.dto.RegionAdminMissionDetailResponse;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetRegionAdminMissionDetailUseCase {

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final MissionService missionService;

    public GetRegionAdminMissionDetailUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        MissionService missionService
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.missionService = missionService;
    }

    @Transactional(readOnly = true)
    public RegionAdminMissionDetailResponse get(Long userId, Long missionId) {
        Long authorizedRegionId = regionAdminAuthorizationService.requireAuthorizedRegionId(userId);
        Mission mission = missionService.findMissionDetail(missionId);
        if (!authorizedRegionId.equals(mission.getRegion().getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return RegionAdminMissionDetailResponse.from(mission);
    }
}
