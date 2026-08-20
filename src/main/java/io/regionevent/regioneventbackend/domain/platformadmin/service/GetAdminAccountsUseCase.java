package io.regionevent.regioneventbackend.domain.platformadmin.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAssignmentService;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;

@Service
public class GetAdminAccountsUseCase {

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final PlatformAdminAssignmentService platformAdminAssignmentService;

    public GetAdminAccountsUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        PlatformAdminAssignmentService platformAdminAssignmentService
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.platformAdminAssignmentService = platformAdminAssignmentService;
    }

    @Transactional(readOnly = true)
    public List<AdminAccountListInfo> get(Long actorUserId) {
        platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(actorUserId);
        return AdminAccountListInfo.from(platformAdminAssignmentService.findPlatformAdminAccountList());
    }
}
