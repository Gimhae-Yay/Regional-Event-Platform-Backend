package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MissionProgressVisitCompletionAdapterTest {

    private static final Long VISIT_ID = 22L;
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000618");

    private final RecordMissionProgressUseCase recordMissionProgressUseCase = mock(
        RecordMissionProgressUseCase.class
    );
    private final MissionProgressVisitCompletionAdapter adapter = new MissionProgressVisitCompletionAdapter(
        recordMissionProgressUseCase
    );

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void recordAfterCommit_커밋전에는실행하지않고커밋후실행한다() {
        TransactionSynchronizationManager.initSynchronization();

        adapter.recordAfterCommit(VISIT_ID, REQUEST_ID);

        verify(recordMissionProgressUseCase, never()).record(VISIT_ID, REQUEST_ID);
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(TransactionSynchronization::afterCommit);
        verify(recordMissionProgressUseCase).record(VISIT_ID, REQUEST_ID);
    }

    @Test
    void recordAfterCommit_롤백이면실행하지않는다() {
        TransactionSynchronizationManager.initSynchronization();

        adapter.recordAfterCommit(VISIT_ID, REQUEST_ID);
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(synchronization -> synchronization.afterCompletion(
                TransactionSynchronization.STATUS_ROLLED_BACK
            ));

        verify(recordMissionProgressUseCase, never()).record(VISIT_ID, REQUEST_ID);
    }

    @Test
    void recordAfterCommit_트랜잭션동기화가없으면즉시실행한다() {
        adapter.recordAfterCommit(VISIT_ID, REQUEST_ID);

        verify(recordMissionProgressUseCase).record(VISIT_ID, REQUEST_ID);
    }

    @Test
    void recordAfterCommit_진행도실패를호출자에게전파하지않는다() {
        doThrow(new IllegalStateException("mission progress failed"))
            .when(recordMissionProgressUseCase)
            .record(VISIT_ID, REQUEST_ID);

        assertThatCode(() -> adapter.recordAfterCommit(VISIT_ID, REQUEST_ID))
            .doesNotThrowAnyException();
    }
}
