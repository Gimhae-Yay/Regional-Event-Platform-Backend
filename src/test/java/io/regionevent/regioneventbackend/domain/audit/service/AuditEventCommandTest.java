package io.regionevent.regioneventbackend.domain.audit.service;

import static org.junit.jupiter.api.Assertions.assertAll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;

class AuditEventCommandTest {

    @Test
    void 전체_단위_계약을_보존한다() {
        assertAll(
            () -> new AuditEventCommandTest().성공_감사_이벤트는_대상_식별자가_필수다(),
            () -> new AuditEventCommandTest().성공_감사_이벤트는_도착_상태가_필수다(),
            () -> new AuditEventCommandTest().성공_생성_감사_이벤트는_이전_상태_없이_생성할_수_있다(),
            () -> new AuditEventCommandTest().실패_감사_이벤트는_대상과_상태_미확정을_허용한다(),
            () -> new AuditEventCommandTest().실패_감사_이벤트는_사유_코드가_없으면_생성할_수_없다(),
            () -> new AuditEventCommandTest().실패_감사_이벤트는_공백_사유_코드를_허용하지_않는다(),
            () -> new AuditEventCommandTest().증빙_참조는_앞뒤_공백을_제거해_보존한다(),
            () -> new AuditEventCommandTest().증빙_참조는_공백만_있거나_500자를_초과하면_거부한다(),
            () -> new AuditEventCommandTest().특권_변경_감사_이벤트는_증빙_참조가_필수다()
        );
    }

    void 성공_감사_이벤트는_대상_식별자가_필수다() {
        assertThatIllegalArgumentException().isThrownBy(
            () -> createCommand(AuditEventResult.SUCCESS, null, "PENDING", "PUBLISHED")
        );
    }

    void 성공_감사_이벤트는_도착_상태가_필수다() {
        assertThatIllegalArgumentException().isThrownBy(
            () -> createCommand(AuditEventResult.SUCCESS, 101L, "PENDING", null)
        );
    }

    void 성공_생성_감사_이벤트는_이전_상태_없이_생성할_수_있다() {
        assertThatCode(
            () -> createCommand(AuditEventResult.SUCCESS, 101L, null, "PENDING")
        ).doesNotThrowAnyException();
    }

    void 실패_감사_이벤트는_대상과_상태_미확정을_허용한다() {
        assertThatCode(
            () -> createCommand(AuditEventResult.FAILURE, null, null, null)
        ).doesNotThrowAnyException();
    }

    void 실패_감사_이벤트는_사유_코드가_없으면_생성할_수_없다() {
        assertThatIllegalArgumentException().isThrownBy(
            () -> createCommand(AuditEventResult.FAILURE, null, null, null, null)
        );
    }

    void 실패_감사_이벤트는_공백_사유_코드를_허용하지_않는다() {
        assertThatIllegalArgumentException().isThrownBy(
            () -> createCommand(AuditEventResult.FAILURE, null, null, null, " ")
        );
    }

    void 증빙_참조는_앞뒤_공백을_제거해_보존한다() {
        AuditEventCommand command = createCommandWithEvidenceReference("  OPS-2026-0809-001  ");

        assertThat(command.evidenceReference()).isEqualTo("OPS-2026-0809-001");
    }

    void 증빙_참조는_공백만_있거나_500자를_초과하면_거부한다() {
        assertThatIllegalArgumentException().isThrownBy(
            () -> createCommandWithEvidenceReference("   ")
        );
        assertThatIllegalArgumentException().isThrownBy(
            () -> createCommandWithEvidenceReference("a".repeat(501))
        );
    }

    void 특권_변경_감사_이벤트는_증빙_참조가_필수다() {
        assertThatIllegalArgumentException().isThrownBy(
            () -> createCommandWithEvidenceReference(
                AuditEventTargetType.PLATFORM_ADMIN_ASSIGNMENT,
                null
            )
        );
        assertThatIllegalArgumentException().isThrownBy(
            () -> createCommandWithEvidenceReference(
                AuditEventTargetType.USER_ROLE_ASSIGNMENT,
                null
            )
        );
    }

    private AuditEventCommand createCommand(
        AuditEventResult result,
        Long targetId,
        String previousState,
        String nextState
    ) {
        return createCommand(result, targetId, previousState, nextState, "CONTENT_APPROVED");
    }

    private AuditEventCommand createCommand(
        AuditEventResult result,
        Long targetId,
        String previousState,
        String nextState,
        String reasonCode
    ) {
        return new AuditEventCommand(
            UUID.fromString("00000000-0000-0000-0000-000000000004"),
            null,
            AuditEventTargetType.CONTENT,
            targetId,
            previousState,
            nextState,
            result,
            reasonCode,
            null,
            Instant.parse("2026-07-31T00:00:00Z")
        );
    }

    private AuditEventCommand createCommandWithEvidenceReference(String evidenceReference) {
        return createCommandWithEvidenceReference(AuditEventTargetType.CONTENT, evidenceReference);
    }

    private AuditEventCommand createCommandWithEvidenceReference(
        AuditEventTargetType targetType,
        String evidenceReference
    ) {
        return new AuditEventCommand(
            UUID.fromString("00000000-0000-0000-0000-000000000004"),
            null,
            targetType,
            101L,
            "PENDING",
            "PUBLISHED",
            AuditEventResult.SUCCESS,
            "CONTENT_APPROVED",
            evidenceReference,
            null,
            Instant.parse("2026-07-31T00:00:00Z")
        );
    }
}
