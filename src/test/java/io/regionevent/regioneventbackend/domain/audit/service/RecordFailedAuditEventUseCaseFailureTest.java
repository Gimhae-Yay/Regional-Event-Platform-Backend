package io.regionevent.regioneventbackend.domain.audit.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;

@DataJpaTest
@Import(RecordFailedAuditEventUseCase.class)
class RecordFailedAuditEventUseCaseFailureTest {

    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;

    @MockitoBean
    private AuditEventService auditEventService;

    @MockitoBean
    private AuditEventActorLinkService auditEventActorLinkService;

    @Autowired
    RecordFailedAuditEventUseCaseFailureTest(
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase
    ) {
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
    }

    @Test
    void record_독립_감사기록_저장에_실패해도_원래_롤백을_전파하지_않는다() {
        when(auditEventService.record(any())).thenThrow(new IllegalStateException("audit storage failure"));
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            UUID.randomUUID(),
            null,
            AuditEventTargetType.CONTENT,
            1L,
            "ENDED",
            null,
            AuditEventResult.FAILURE,
            "NOT_FOUND",
            null,
            Instant.parse("2026-08-05T00:00:00Z")
        ));

        TestTransaction.flagForRollback();
        assertThatCode(TestTransaction::end).doesNotThrowAnyException();
    }
}
