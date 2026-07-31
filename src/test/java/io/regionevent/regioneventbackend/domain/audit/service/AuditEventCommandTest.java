package io.regionevent.regioneventbackend.domain.audit.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;

class AuditEventCommandTest {

    @Test
    void 성공_감사_이벤트는_대상_식별자가_필수다() {
        assertThatIllegalArgumentException().isThrownBy(
            () -> createCommand(AuditEventResult.SUCCESS, null, "PENDING", "PUBLISHED")
        );
    }

    @Test
    void 성공_감사_이벤트는_도착_상태가_필수다() {
        assertThatIllegalArgumentException().isThrownBy(
            () -> createCommand(AuditEventResult.SUCCESS, 101L, "PENDING", null)
        );
    }

    @Test
    void 성공_생성_감사_이벤트는_이전_상태_없이_생성할_수_있다() {
        assertThatCode(
            () -> createCommand(AuditEventResult.SUCCESS, 101L, null, "PENDING")
        ).doesNotThrowAnyException();
    }

    @Test
    void 실패_감사_이벤트는_대상과_상태_미확정을_허용한다() {
        assertThatCode(
            () -> createCommand(AuditEventResult.FAILURE, null, null, null)
        ).doesNotThrowAnyException();
    }

    private AuditEventCommand createCommand(
        AuditEventResult result,
        Long targetId,
        String previousState,
        String nextState
    ) {
        return new AuditEventCommand(
            UUID.fromString("00000000-0000-0000-0000-000000000004"),
            null,
            AuditEventTargetType.CONTENT,
            targetId,
            previousState,
            nextState,
            result,
            "CONTENT_APPROVED",
            null,
            Instant.parse("2026-07-31T00:00:00Z")
        );
    }
}
