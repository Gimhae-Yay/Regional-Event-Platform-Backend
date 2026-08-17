package io.regionevent.regioneventbackend.domain.mission.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.mission.dto.OperatorMissionDetailResponse;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetOperatorMissionDetailUseCase {

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final MissionService missionService;

    public GetOperatorMissionDetailUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        MissionService missionService
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.missionService = missionService;
    }

    @Transactional(readOnly = true)
    public OperatorMissionDetailResponse get(Long userId, Long missionId) {
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(userId);
        Mission mission = missionService.findMissionDetail(missionId);
        if (!operator.region().getRegionId().equals(mission.getRegion().getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return OperatorMissionDetailResponse.from(mission);
    }
}
