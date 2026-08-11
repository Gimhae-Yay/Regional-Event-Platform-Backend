package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionEarlyEndReasonCode;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class EndOperatorMissionUseCase {

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final MissionService missionService;
    private final MissionParticipationService missionParticipationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final Clock clock;

    public EndOperatorMissionUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        MissionService missionService,
        MissionParticipationService missionParticipationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        Clock clock
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.missionService = missionService;
        this.missionParticipationService = missionParticipationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public EndOperatorMissionResult end(
        Long userId,
        Long missionId,
        String reasonCode,
        UUID requestId
    ) {
        validateCommand(userId, missionId, reasonCode, requestId);
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(userId);
        Mission mission = missionService.findForUpdate(missionId);
        MissionStatus previousState = mission.getStatus();

        try {
            validateLockedMission(operator, mission);
            missionParticipationService.endInProgress(missionId);
            Instant endedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            Mission endedMission = missionService.end(mission, endedAt);
            recordSuccess(requestId, endedMission, operator, reasonCode, endedAt);
            return EndOperatorMissionResult.from(endedMission);
        } catch (BusinessException exception) {
            recordFailure(requestId, mission, previousState, operator, exception.getErrorCode());
            throw exception;
        } catch (RuntimeException exception) {
            recordFailure(requestId, mission, previousState, operator, ErrorCode.INTERNAL_SERVER_ERROR);
            throw exception;
        }
    }

    private void validateCommand(
        Long userId,
        Long missionId,
        String reasonCode,
        UUID requestId
    ) {
        if (userId == null
            || userId <= 0
            || missionId == null
            || missionId <= 0
            || !MissionEarlyEndReasonCode.isSupported(reasonCode)
            || requestId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateLockedMission(
        AuthorizedOperator operator,
        Mission mission
    ) {
        if (!operator.region().getRegionId().equals(mission.getRegion().getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (mission.getStatus() != MissionStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.MISSION_STATE_CONFLICT);
        }
    }

    private void recordSuccess(
        UUID requestId,
        Mission mission,
        AuthorizedOperator operator,
        String reasonCode,
        Instant endedAt
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            mission.getRegion(),
            AuditEventTargetType.MISSION,
            mission.getMissionId(),
            MissionStatus.PUBLISHED.name(),
            MissionStatus.ENDED.name(),
            AuditEventResult.SUCCESS,
            reasonCode,
            new AuditEventActor(operator.roleAssignment()),
            endedAt
        ));
    }

    private void recordFailure(
        UUID requestId,
        Mission mission,
        MissionStatus previousState,
        AuthorizedOperator operator,
        ErrorCode errorCode
    ) {
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            mission.getRegion(),
            AuditEventTargetType.MISSION,
            mission.getMissionId(),
            previousState.name(),
            null,
            AuditEventResult.FAILURE,
            errorCode.code(),
            new AuditEventActor(operator.roleAssignment()),
            clock.instant().truncatedTo(ChronoUnit.MICROS)
        ));
    }
}
