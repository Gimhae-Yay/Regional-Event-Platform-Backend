package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionProgress;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.service.MissionParticipationReadService.MissionProgressCandidate;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.service.VisitService;

@Service
public class RecordMissionProgressUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecordMissionProgressUseCase.class);
    private static final String TARGET_PROCESSING_FAILED = "MISSION_PROGRESS_TARGET_PROCESSING_FAILED";
    private static final String DUPLICATE_READ_FAILED = "MISSION_PROGRESS_DUPLICATE_READ_FAILED";

    private final VisitService visitService;
    private final MissionParticipationReadService missionParticipationReadService;
    private final MissionService missionService;
    private final MissionParticipationService missionParticipationService;
    private final MissionTargetContentService missionTargetContentService;
    private final MissionProgressService missionProgressService;
    private final MissionProgressDuplicateReadService missionProgressDuplicateReadService;
    private final TransactionTemplate targetTransactionTemplate;

    public RecordMissionProgressUseCase(
        VisitService visitService,
        MissionParticipationReadService missionParticipationReadService,
        MissionService missionService,
        MissionParticipationService missionParticipationService,
        MissionTargetContentService missionTargetContentService,
        MissionProgressService missionProgressService,
        MissionProgressDuplicateReadService missionProgressDuplicateReadService,
        PlatformTransactionManager transactionManager
    ) {
        this.visitService = visitService;
        this.missionParticipationReadService = missionParticipationReadService;
        this.missionService = missionService;
        this.missionParticipationService = missionParticipationService;
        this.missionTargetContentService = missionTargetContentService;
        this.missionProgressService = missionProgressService;
        this.missionProgressDuplicateReadService = missionProgressDuplicateReadService;
        targetTransactionTemplate = new TransactionTemplate(transactionManager);
        targetTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(
        Long visitId,
        UUID requestId
    ) {
        Visit sourceVisit = visitService.findMissionProgressSource(visitId).orElse(null);
        if (sourceVisit == null) {
            return;
        }

        List<MissionProgressCandidate> candidates = missionParticipationReadService.findProgressCandidates(
            sourceVisit.getUser().getUserId(),
            sourceVisit.getRegion().getRegionId()
        );
        for (MissionProgressCandidate candidate : candidates) {
            recordCandidate(visitId, requestId, candidate);
        }
    }

    private void recordCandidate(
        Long visitId,
        UUID requestId,
        MissionProgressCandidate candidate
    ) {
        try {
            targetTransactionTemplate.executeWithoutResult(status -> recordInCurrentTransaction(
                visitId,
                candidate
            ));
        } catch (DataIntegrityViolationException ignored) {
            recoverDuplicate(visitId, requestId, candidate);
        } catch (RuntimeException ignored) {
            logTargetFailure(requestId, visitId, candidate, TARGET_PROCESSING_FAILED);
        }
    }

    private void recordInCurrentTransaction(
        Long visitId,
        MissionProgressCandidate candidate
    ) {
        Mission mission = missionService.findMissionForParticipationUpdate(candidate.missionId());
        MissionParticipation participation = missionParticipationService
            .findByIdForProgressUpdate(candidate.participationId())
            .orElse(null);
        if (participation == null) {
            return;
        }
        Instant operationAt = missionService.findCurrentDatabaseTime();
        Visit visit = visitService.findMissionProgressSourceInCurrentTransaction(visitId).orElse(null);
        if (!canRecord(mission, participation, visit, operationAt)) {
            return;
        }

        Long participationId = participation.getMissionParticipationId();
        if (missionProgressService.existsByVisitId(participationId, visitId)) {
            return;
        }
        if (!matchesMissionCondition(mission, participationId, visit.getContent().getContentId())) {
            return;
        }

        missionProgressService.create(new MissionProgress(
            participation,
            visit,
            visit.getContent(),
            operationAt
        ));
        if (hasCompletedMission(mission, participationId)) {
            missionParticipationService.complete(participation, operationAt);
        }
    }

    private boolean canRecord(
        Mission mission,
        MissionParticipation participation,
        Visit visit,
        Instant operationAt
    ) {
        if (visit == null
            || mission.getStatus() != MissionStatus.PUBLISHED
            || !mission.getEndsAt().isAfter(operationAt)
            || participation.getStatus() != MissionParticipationStatus.IN_PROGRESS) {
            return false;
        }
        return sameId(mission.getMissionId(), participation.getMission().getMissionId())
            && sameId(visit.getUser().getUserId(), participation.getUser().getUserId())
            && sameId(visit.getRegion().getRegionId(), mission.getRegion().getRegionId())
            && !visit.getCheckedAt().isBefore(participation.getJoinedAt());
    }

    private boolean matchesMissionCondition(
        Mission mission,
        Long participationId,
        Long contentId
    ) {
        if (mission.getConditionType() == MissionConditionType.VISIT_COUNT) {
            return true;
        }
        return missionTargetContentService.contains(mission.getMissionId(), contentId)
            && !missionProgressService.existsByContentId(participationId, contentId);
    }

    private boolean hasCompletedMission(
        Mission mission,
        Long participationId
    ) {
        long progressCount = missionProgressService.countByParticipationId(participationId);
        if (mission.getConditionType() == MissionConditionType.VISIT_COUNT) {
            return progressCount >= mission.getRequiredVisitCount();
        }
        return progressCount >= missionTargetContentService.countRequiredContents(mission.getMissionId());
    }

    private void recoverDuplicate(
        Long visitId,
        UUID requestId,
        MissionProgressCandidate candidate
    ) {
        try {
            if (!missionProgressDuplicateReadService.exists(candidate.participationId(), visitId)) {
                logTargetFailure(requestId, visitId, candidate, TARGET_PROCESSING_FAILED);
            }
        } catch (RuntimeException ignored) {
            logTargetFailure(requestId, visitId, candidate, DUPLICATE_READ_FAILED);
        }
    }

    private void logTargetFailure(
        UUID requestId,
        Long visitId,
        MissionProgressCandidate candidate,
        String errorCode
    ) {
        log.atError()
            .addKeyValue("requestId", requestId)
            .addKeyValue("visitId", visitId)
            .addKeyValue("missionId", candidate.missionId())
            .addKeyValue("missionParticipationId", candidate.participationId())
            .addKeyValue("errorCode", errorCode)
            .log("Mission progress processing failed");
    }

    private boolean sameId(Long expected, Long actual) {
        return expected != null && expected.equals(actual);
    }
}
