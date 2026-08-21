package io.regionevent.regioneventbackend.domain.platformadmin.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;

@Service
public class GetPlatformAdminMeUseCase {

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;

    public GetPlatformAdminMeUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
    }

    @Transactional(readOnly = true)
    public Long get(Long actorUserId) {
        platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(actorUserId);
        return actorUserId;
    }
}
