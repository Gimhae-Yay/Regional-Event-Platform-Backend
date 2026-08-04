package io.regionevent.regioneventbackend.domain.operator.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.operator.dto.PendingOperatorApplicationsResponse;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetPendingOperatorApplicationsUseCase {

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final OperatorApplicationService operatorApplicationService;

    public GetPendingOperatorApplicationsUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        OperatorApplicationService operatorApplicationService
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.operatorApplicationService = operatorApplicationService;
    }

    @Transactional(readOnly = true)
    public PendingOperatorApplicationsResponse get(Long userId, String status) {
        Long regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(userId);
        if (!OperatorApplicationStatus.PENDING.name().equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return PendingOperatorApplicationsResponse.from(
            operatorApplicationService.findPendingApplications(regionId)
        );
    }
}
