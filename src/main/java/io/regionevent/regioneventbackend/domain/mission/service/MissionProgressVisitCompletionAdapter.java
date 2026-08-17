package io.regionevent.regioneventbackend.domain.mission.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class MissionProgressVisitCompletionAdapter {

    private static final Logger log = LoggerFactory.getLogger(MissionProgressVisitCompletionAdapter.class);
    private static final String DISPATCH_FAILED = "MISSION_PROGRESS_DISPATCH_FAILED";

    private final RecordMissionProgressUseCase recordMissionProgressUseCase;

    public MissionProgressVisitCompletionAdapter(RecordMissionProgressUseCase recordMissionProgressUseCase) {
        this.recordMissionProgressUseCase = recordMissionProgressUseCase;
    }

    public void recordAfterCommit(
        Long visitId,
        UUID requestId
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            recordSafely(visitId, requestId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                recordSafely(visitId, requestId);
            }
        });
    }

    private void recordSafely(
        Long visitId,
        UUID requestId
    ) {
        try {
            recordMissionProgressUseCase.record(visitId, requestId);
        } catch (RuntimeException ignored) {
            log.atError()
                .addKeyValue("requestId", requestId)
                .addKeyValue("visitId", visitId)
                .addKeyValue("errorCode", DISPATCH_FAILED)
                .log("Mission progress dispatch failed");
        }
    }
}
