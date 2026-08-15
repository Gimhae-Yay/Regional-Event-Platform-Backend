package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
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
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService.AuthorizedRegionAdmin;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class RejectRegionAdminMissionUseCase {

    private static final Set<String> ALLOWED_REASON_CODES = Set.of(
        "MISSION_INFORMATION_INCOMPLETE",
        "MISSION_CONDITION_INVALID",
        "MISSION_TARGET_CONTENT_INVALID",
        "MISSION_REWARD_POLICY_INVALID",
        "MISSION_SCHEDULE_INVALID"
    );

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final MissionService missionService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final Clock clock;

    public RejectRegionAdminMissionUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        MissionService missionService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        Clock clock
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.missionService = missionService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public RejectRegionAdminMissionResult reject(
        Long userId,
        Long missionId,
        String reasonCode,
        UUID requestId
    ) {
        validateReasonCode(reasonCode);
        AuthorizedRegionAdmin regionAdmin = regionAdminAuthorizationService
            .requireAuthorizedRegionAdminForUpdate(userId);
        Mission mission = missionService.findMission(missionId);
        MissionStatus previousState = mission.getStatus();

        try {
            validateRegionScope(regionAdmin, mission);
            mission = missionService.findForUpdate(missionId);
            previousState = mission.getStatus();
            validateRegionScope(regionAdmin, mission);
            Mission rejectedMission = missionService.reject(mission);
            Instant rejectedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            recordAuditEventUseCase.record(new AuditEventCommand(
                requestId,
                rejectedMission.getRegion(),
                AuditEventTargetType.MISSION,
                rejectedMission.getMissionId(),
                MissionStatus.PENDING_REVIEW.name(),
                MissionStatus.DRAFT.name(),
                AuditEventResult.SUCCESS,
                reasonCode,
                new AuditEventActor(regionAdmin.roleAssignment()),
                rejectedAt
            ));
            return new RejectRegionAdminMissionResult(
                rejectedMission.getMissionId(),
                rejectedMission.getStatus(),
                rejectedAt
            );
        } catch (BusinessException exception) {
            recordFailure(requestId, mission, previousState, regionAdmin, exception.getErrorCode());
            throw exception;
        } catch (RuntimeException exception) {
            recordFailure(requestId, mission, previousState, regionAdmin, ErrorCode.INTERNAL_SERVER_ERROR);
            throw exception;
        }
    }

    private void validateReasonCode(String reasonCode) {
        if (reasonCode == null || !ALLOWED_REASON_CODES.contains(reasonCode)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateRegionScope(
        AuthorizedRegionAdmin regionAdmin,
        Mission mission
    ) {
        if (!regionAdmin.region().getRegionId().equals(mission.getRegion().getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void recordFailure(
        UUID requestId,
        Mission mission,
        MissionStatus previousState,
        AuthorizedRegionAdmin regionAdmin,
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
            new AuditEventActor(regionAdmin.roleAssignment()),
            clock.instant().truncatedTo(ChronoUnit.MICROS)
        ));
    }
}
