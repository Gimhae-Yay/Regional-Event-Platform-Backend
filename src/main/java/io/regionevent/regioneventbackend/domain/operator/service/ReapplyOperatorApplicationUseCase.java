package io.regionevent.regioneventbackend.domain.operator.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.operator.dto.CreateOperatorApplicationRequest;
import io.regionevent.regioneventbackend.domain.operator.dto.CreateOperatorApplicationResponse;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ReapplyOperatorApplicationUseCase {

    private final AppUserService appUserService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final OperatorApplicationService operatorApplicationService;
    private final RegionService regionService;

    public ReapplyOperatorApplicationUseCase(
        AppUserService appUserService,
        UserRoleAssignmentService userRoleAssignmentService,
        OperatorApplicationService operatorApplicationService,
        RegionService regionService
    ) {
        this.appUserService = appUserService;
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.operatorApplicationService = operatorApplicationService;
        this.regionService = regionService;
    }

    @Transactional
    public CreateOperatorApplicationResponse reapply(Long userId, CreateOperatorApplicationRequest request) {
        AppUser user = appUserService.findActiveUserForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        validateNoOperatorRole(userId);
        validateReapplicationAllowed(user);

        Region region = regionService.findPublicRegion(request.requestedRegionId());
        OperatorApplication application = operatorApplicationService.createPendingApplication(
            user,
            region,
            request.businessInformation()
        );
        return new CreateOperatorApplicationResponse(
            application.getOperatorApplicationId(),
            region.getRegionId(),
            application.getStatus().name()
        );
    }

    private void validateNoOperatorRole(Long userId) {
        var roles = userRoleAssignmentService.findRolesByUserId(userId);
        if (roles.contains(UserRole.OPERATOR) || roles.contains(UserRole.REGION_ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateReapplicationAllowed(AppUser user) {
        if (operatorApplicationService.hasPendingApplication(user)) {
            throw new BusinessException(ErrorCode.OPERATOR_APPLICATION_PENDING);
        }
        if (!operatorApplicationService.hasRejectedApplication(user)) {
            throw new BusinessException(ErrorCode.OPERATOR_APPLICATION_REAPPLICATION_NOT_ALLOWED);
        }
    }
}
