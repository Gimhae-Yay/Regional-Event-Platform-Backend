package io.regionevent.regioneventbackend.domain.audit.service;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;

class RecordAuditEventUseCaseUnitTest {

    @Test
    void 성공_기록은_실패_결과를_받지_않는다() {
        RecordAuditEventUseCase useCase = new RecordAuditEventUseCase(
            mock(AuditEventService.class),
            mock(AuditEventActorLinkService.class)
        );

        assertThatIllegalArgumentException().isThrownBy(
            () -> useCase.record(createCommand("RESERVATION_NOT_FOUND"))
        );
    }

    @Test
    void 감사_입력은_개인정보와_원문_형식의_사유를_거부한다() {
        assertThatIllegalArgumentException().isThrownBy(() -> createCommand("user@example.com"));
        assertThatIllegalArgumentException().isThrownBy(() -> createCommand("idempotency-key"));
        assertThatIllegalArgumentException().isThrownBy(() -> createCommand("eyJhbGciOiJIUzI1NiJ9.payload.signature"));
        assertThatIllegalArgumentException().isThrownBy(() -> createCommand("550e8400-e29b-41d4-a716-446655440000"));
    }

    private AuditEventCommand createCommand(String reasonCode) {
        return new AuditEventCommand(
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            null,
            AuditEventTargetType.RESERVATION,
            null,
            null,
            null,
            AuditEventResult.FAILURE,
            reasonCode,
            null,
            Instant.parse("2026-07-31T00:00:00Z")
        );
    }
}
