package io.regionevent.regioneventbackend.domain.platformadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAssignmentService;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;

class CreateAdminAccountUseCaseTest {

    @Test
    void create_슈퍼관리자요청_특권계정배정감사를생성한다() {
        PlatformAdminAuthorizationService authorizationService = mock(PlatformAdminAuthorizationService.class);
        AppUserService appUserService = mock(AppUserService.class);
        PlatformAdminAssignmentService assignmentService = mock(PlatformAdminAssignmentService.class);
        RecordAuditEventUseCase auditEventUseCase = mock(RecordAuditEventUseCase.class);
        Instant now = Instant.parse("2026-08-09T06:00:00Z");
        CreateAdminAccountUseCase useCase = new CreateAdminAccountUseCase(
            authorizationService,
            appUserService,
            assignmentService,
            auditEventUseCase
        );
        PlatformAdminAssignment actor = platformAdminAssignment(10L, 100L, PlatformAdminGrade.SUPER_ADMIN);
        AppUser user = mock(AppUser.class);
        when(user.getUserId()).thenReturn(101L);
        PlatformAdminAssignment assignment = mock(PlatformAdminAssignment.class);
        when(assignment.getPlatformAdminAssignmentId()).thenReturn(201L);
        when(assignment.getGrade()).thenReturn(PlatformAdminGrade.PLATFORM_ADMIN);
        when(assignment.getStatus()).thenReturn(PlatformAdminAssignmentStatus.ACTIVE);
        when(assignment.getGrantedAt()).thenReturn(now);
        when(authorizationService.requireAuthorizedSuperAdminForUpdate(100L)).thenReturn(actor);
        when(appUserService.createActivePrivilegedUser(any(), any(), any(), any())).thenReturn(user);
        when(assignmentService.createActiveAssignment(user, PlatformAdminGrade.PLATFORM_ADMIN)).thenReturn(assignment);

        CreateAdminAccountResult result = useCase.create(
            100L,
            new CreateAdminAccountUseCase.CreateAdminAccountCommand(
                "admin@example.com", "LocalStamp!2026", "관리자", "01012345678",
                "PLATFORM_ADMIN", "ADMIN_ACCOUNT_CREATION", "OPS-2026-0806-001"
            ),
            UUID.randomUUID()
        );

        assertThat(result).isEqualTo(new CreateAdminAccountResult(
            101L, 201L, "PLATFORM_ADMIN", "ACTIVE", now
        ));
        verify(assignmentService).createActiveAssignment(user, PlatformAdminGrade.PLATFORM_ADMIN);
        verify(auditEventUseCase).record(any());
    }

    private PlatformAdminAssignment platformAdminAssignment(
        Long assignmentId,
        Long userId,
        PlatformAdminGrade grade
    ) {
        AppUser appUser = mock(AppUser.class);
        when(appUser.getUserId()).thenReturn(userId);
        when(appUser.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(appUser.getAccountKind()).thenReturn(AppUserAccountKind.PRIVILEGED);
        PlatformAdminAssignment assignment = mock(PlatformAdminAssignment.class);
        when(assignment.getPlatformAdminAssignmentId()).thenReturn(assignmentId);
        when(assignment.getAppUser()).thenReturn(appUser);
        when(assignment.getGrade()).thenReturn(grade);
        when(assignment.isActive()).thenReturn(true);
        return assignment;
    }
}
