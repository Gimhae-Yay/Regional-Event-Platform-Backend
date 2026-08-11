package io.regionevent.regioneventbackend.domain.mission.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.service.MissionHistoryReadService;
import io.regionevent.regioneventbackend.domain.mission.dto.RegionAdminMissionHistoryResponse;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetRegionAdminMissionHistoryUseCase {

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final MissionService missionService;
    private final MissionHistoryReadService missionHistoryReadService;

    public GetRegionAdminMissionHistoryUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        MissionService missionService,
        MissionHistoryReadService missionHistoryReadService
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.missionService = missionService;
        this.missionHistoryReadService = missionHistoryReadService;
    }

    @Transactional(readOnly = true)
    public RegionAdminMissionHistoryResponse get(Long userId, Long missionId) {
        Long authorizedRegionId = regionAdminAuthorizationService.requireAuthorizedRegionId(userId);
        Mission mission = missionService.findMissionDetail(missionId);
        if (!authorizedRegionId.equals(mission.getRegion().getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return RegionAdminMissionHistoryResponse.from(
            missionId,
            missionHistoryReadService.findAll(missionId)
        );
    }
}
