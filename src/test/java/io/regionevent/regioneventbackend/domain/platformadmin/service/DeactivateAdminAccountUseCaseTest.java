package io.regionevent.regioneventbackend.domain.platformadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;

import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAssignmentService;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class DeactivateAdminAccountUseCaseTest {

    private static final Long ACTOR_USER_ID = 100L;
    private static final Instant NOW = Instant.parse("2026-08-10T01:00:00Z");

    @Test
    void deactivate_다른활성고권한계정을비활성화하고감사를기록한다() {
        PlatformAdminAuthorizationService authorizationService = mock(PlatformAdminAuthorizationService.class);
        PlatformAdminAssignmentService assignmentService = mock(PlatformAdminAssignmentService.class);
        RecordAuditEventUseCase auditEventUseCase = mock(RecordAuditEventUseCase.class);
        DeactivateAdminAccountUseCase useCase = new DeactivateAdminAccountUseCase(
            authorizationService,
            assignmentService,
            auditEventUseCase,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        PlatformAdminAssignment actor = assignment(1L, ACTOR_USER_ID, PlatformAdminGrade.SUPER_ADMIN);
        PlatformAdminAssignment target = assignment(2L, 101L, PlatformAdminGrade.PLATFORM_ADMIN);
        when(authorizationService.requireAuthorizedSuperAdmin(ACTOR_USER_ID)).thenReturn(actor);
        when(assignmentService.findActiveSuperAdminsForUpdate()).thenReturn(List.of(actor));
        when(assignmentService.findAssignmentForUpdate(101L)).thenReturn(Optional.of(target));

        DeactivateAdminAccountResult result = useCase.deactivate(
            ACTOR_USER_ID,
            101L,
            new DeactivateAdminAccountUseCase.DeactivateAdminAccountCommand(
                "ADMIN_ACCOUNT_INACTIVATION",
                "OPS-2026-0810-001"
            ),
            UUID.randomUUID()
        );

        assertThat(result).isEqualTo(new DeactivateAdminAccountResult(
            101L,
            2L,
            "PLATFORM_ADMIN",
            "INACTIVE",
            NOW
        ));
        assertThat(target.getInactiveReasonCode()).isEqualTo("ADMIN_ACCOUNT_INACTIVATION");
        verify(auditEventUseCase).record(any());
    }

    @Test
    void deactivate_마지막슈퍼관리자면_충돌오류를반환하고감사를기록하지않는다() {
        PlatformAdminAuthorizationService authorizationService = mock(PlatformAdminAuthorizationService.class);
        PlatformAdminAssignmentService assignmentService = mock(PlatformAdminAssignmentService.class);
        RecordAuditEventUseCase auditEventUseCase = mock(RecordAuditEventUseCase.class);
        DeactivateAdminAccountUseCase useCase = new DeactivateAdminAccountUseCase(
            authorizationService,
            assignmentService,
            auditEventUseCase,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        PlatformAdminAssignment actor = assignment(1L, ACTOR_USER_ID, PlatformAdminGrade.SUPER_ADMIN);
        PlatformAdminAssignment target = assignment(2L, 101L, PlatformAdminGrade.SUPER_ADMIN);
        when(authorizationService.requireAuthorizedSuperAdmin(ACTOR_USER_ID)).thenReturn(actor);
        when(assignmentService.findActiveSuperAdminsForUpdate()).thenReturn(List.of(target));
        when(assignmentService.findAssignmentForUpdate(101L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> useCase.deactivate(
            ACTOR_USER_ID,
            101L,
            new DeactivateAdminAccountUseCase.DeactivateAdminAccountCommand(
                "ADMIN_ACCOUNT_INACTIVATION",
                "OPS-2026-0810-001"
            ),
            UUID.randomUUID()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_ACCOUNT_DEACTIVATION_CONFLICT)
        );

        assertThat(target.getStatus()).isEqualTo(PlatformAdminAssignmentStatus.ACTIVE);
        verifyNoInteractions(auditEventUseCase);
    }

    private PlatformAdminAssignment assignment(Long assignmentId, Long userId, PlatformAdminGrade grade) {
        AppUser user = mock(AppUser.class);
        when(user.getUserId()).thenReturn(userId);
        when(user.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(user.getAccountKind()).thenReturn(AppUserAccountKind.PRIVILEGED);
        PlatformAdminAssignment assignment = new PlatformAdminAssignment(user, grade);
        ReflectionTestUtils.setField(assignment, "platformAdminAssignmentId", assignmentId);
        return assignment;
    }
}
