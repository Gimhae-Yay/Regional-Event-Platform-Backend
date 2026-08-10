package io.regionevent.regioneventbackend.domain.region.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;

@Service
public class GetPlatformAdminRegionsUseCase {

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final RegionService regionService;

    public GetPlatformAdminRegionsUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        RegionService regionService
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.regionService = regionService;
    }

    @Transactional(readOnly = true)
    public List<PlatformAdminRegionListInfo> get(
        Long actorUserId,
        Boolean isPublic
    ) {
        platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(actorUserId);
        return regionService.findPlatformAdminRegionList(isPublic).stream()
            .map(PlatformAdminRegionListInfo::from)
            .toList();
    }
}
