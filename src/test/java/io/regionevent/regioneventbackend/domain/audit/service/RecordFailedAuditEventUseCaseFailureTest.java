package io.regionevent.regioneventbackend.domain.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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

    private static final UUID REQUEST_ID =
        UUID.fromString("4d7c2044-b64f-4bd5-a718-5390198a6819");

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
    void record_독립_감사저장은_한번만_시도하고_실패원문없이_고정필드_로그를_남긴다() {
        String failureMessage = "admin@example.com token-secret audit storage failure";
        when(auditEventService.record(any())).thenThrow(new IllegalStateException(failureMessage));
        Logger logger = (Logger) LoggerFactory.getLogger(RecordFailedAuditEventUseCase.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            REQUEST_ID,
            null,
            AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST,
            7001L,
            "PENDING",
            null,
            AuditEventResult.FAILURE,
            "CONTENT_STATE_CONFLICT",
            null,
            Instant.parse("2026-08-05T00:00:00Z")
        ));

        try {
            TestTransaction.flagForRollback();
            assertThatCode(TestTransaction::end).doesNotThrowAnyException();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        verify(auditEventService, times(1)).record(any());
        verifyNoInteractions(auditEventActorLinkService);
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage())
                .isEqualTo("Failed audit event write failed")
                .doesNotContain(failureMessage)
                .doesNotContain(IllegalStateException.class.getName());
            assertThat(event.getKeyValuePairs())
                .extracting(pair -> pair.key, pair -> pair.value)
                .containsExactly(
                    tuple("requestId", REQUEST_ID),
                    tuple("targetType", AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST),
                    tuple("targetId", 7001L),
                    tuple("originalErrorCode", "CONTENT_STATE_CONFLICT"),
                    tuple("auditWriteResult", "FAILURE")
                );
            assertThat(event.getThrowableProxy()).isNull();
        });
    }
}
