package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;

@Service
public class EndMissionsUseCase {

    private static final String AUTO_END_REASON_CODE = "MISSION_END_TIME_REACHED";
    private static final String AUTO_END_FAILURE_REASON_CODE = "MISSION_AUTO_END_FAILED";

    private final MissionService missionService;
    private final MissionParticipationService missionParticipationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;

    public EndMissionsUseCase(
        MissionService missionService,
        MissionParticipationService missionParticipationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase
    ) {
        this.missionService = missionService;
        this.missionParticipationService = missionParticipationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
    }

    @Transactional(readOnly = true)
    public List<Long> findAutoEndCandidateIds() {
        return missionService.findAutoEndCandidateIds();
    }

    @Transactional
    public EndMissionSystemResult endBySystem(
        Long missionId,
        UUID requestId
    ) {
        Mission mission = missionService.findForUpdate(missionId);
        if (mission.getStatus() != MissionStatus.PUBLISHED) {
            return EndMissionSystemResult.skipped();
        }

        try {
            Instant operationAt = missionService.findCurrentDatabaseTime();
            if (mission.getEndsAt().isAfter(operationAt)) {
                return EndMissionSystemResult.skipped();
            }

            Mission endedMission = missionService.end(mission, operationAt);
            missionParticipationService.endInProgress(missionId);
            recordAuditEventUseCase.record(new AuditEventCommand(
                requestId,
                endedMission.getRegion(),
                AuditEventTargetType.MISSION,
                missionId,
                MissionStatus.PUBLISHED.name(),
                MissionStatus.ENDED.name(),
                AuditEventResult.SUCCESS,
                AUTO_END_REASON_CODE,
                null,
                operationAt
            ));
            return EndMissionSystemResult.ended();
        } catch (RuntimeException exception) {
            recordFailure(requestId, mission);
            throw exception;
        }
    }

    private void recordFailure(
        UUID requestId,
        Mission mission
    ) {
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            mission.getRegion(),
            AuditEventTargetType.MISSION,
            mission.getMissionId(),
            MissionStatus.PUBLISHED.name(),
            null,
            AuditEventResult.FAILURE,
            AUTO_END_FAILURE_REASON_CODE,
            null,
            missionService.findCurrentDatabaseTime()
        ));
    }
}
