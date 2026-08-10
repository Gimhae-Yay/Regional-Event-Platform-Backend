package io.regionevent.regioneventbackend.domain.user.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetPlatformAdminUsersUseCase {

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final AppUserService appUserService;

    public GetPlatformAdminUsersUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        AppUserService appUserService
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.appUserService = appUserService;
    }

    @Transactional(readOnly = true)
    public List<PlatformAdminUserListInfo> get(Long actorUserId) {
        platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(actorUserId);
        return PlatformAdminUserListInfo.from(appUserService.findPlatformAdminUserList());
    }
}
