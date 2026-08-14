package io.regionevent.regioneventbackend.domain.stampbook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class StampbookProgressVisitCompletionAdapter {

    private static final Logger log = LoggerFactory.getLogger(StampbookProgressVisitCompletionAdapter.class);
    private static final String DISPATCH_FAILED = "STAMPBOOK_PROGRESS_DISPATCH_FAILED";

    private final RecordStampbookProgressUseCase recordStampbookProgressUseCase;

    public StampbookProgressVisitCompletionAdapter(RecordStampbookProgressUseCase recordStampbookProgressUseCase) {
        this.recordStampbookProgressUseCase = recordStampbookProgressUseCase;
    }

    public void recordAfterCommit(Long visitId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            recordSafely(visitId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                recordSafely(visitId);
            }
        });
    }

    private void recordSafely(Long visitId) {
        try {
            recordStampbookProgressUseCase.record(visitId);
        } catch (RuntimeException ignored) {
            log.atError()
                .addKeyValue("visitId", visitId)
                .addKeyValue("errorCode", DISPATCH_FAILED)
                .log("Stampbook progress dispatch failed");
        }
    }
}
