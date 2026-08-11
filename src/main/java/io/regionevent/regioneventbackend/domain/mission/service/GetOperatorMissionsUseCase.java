package io.regionevent.regioneventbackend.domain.mission.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;

@Service
public class GetOperatorMissionsUseCase {

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final MissionService missionService;

    public GetOperatorMissionsUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        MissionService missionService
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.missionService = missionService;
    }

    @Transactional(readOnly = true)
    public OperatorMissionListResult get(
        Long userId,
        MissionStatus status,
        int page,
        int size
    ) {
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(userId);
        return OperatorMissionListResult.from(missionService.findRegionMissions(
            operator.region().getRegionId(),
            status,
            PageRequest.of(page, size)
        ));
    }
}
