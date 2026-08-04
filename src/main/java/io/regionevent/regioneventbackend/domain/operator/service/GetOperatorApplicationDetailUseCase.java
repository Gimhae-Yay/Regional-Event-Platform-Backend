package io.regionevent.regioneventbackend.domain.operator.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.operator.dto.OperatorApplicationDetailResponse;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;

@Service
public class GetOperatorApplicationDetailUseCase {

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final OperatorApplicationService operatorApplicationService;

    public GetOperatorApplicationDetailUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        OperatorApplicationService operatorApplicationService
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.operatorApplicationService = operatorApplicationService;
    }

    @Transactional(readOnly = true)
    public OperatorApplicationDetailResponse get(Long userId, Long operatorApplicationId) {
        Long regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(userId);
        return OperatorApplicationDetailResponse.from(
            operatorApplicationService.findDetail(operatorApplicationId, regionId)
        );
    }
}
