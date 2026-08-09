package io.regionevent.regioneventbackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class ChangeRegionAdminRoleUseCaseTest {

    private static final Long ACTOR_USER_ID = 100L;
    private static final Long TARGET_USER_ID = 200L;
    private static final Long PREVIOUS_REGION_ID = 10L;
    private static final Long REQUESTED_REGION_ID = 20L;
    private static final Instant CHANGED_AT = Instant.parse("2026-08-09T00:00:00Z");
    private static final UUID REQUEST_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String REASON_CODE = "REGION_ADMIN_APPOINTMENT";
    private static final String EVIDENCE_REFERENCE = "OPS-2026-0809-001";

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService =
        mock(PlatformAdminAuthorizationService.class);
    private final AppUserService appUserService = mock(AppUserService.class);
    private final UserRoleAssignmentService userRoleAssignmentService =
        mock(UserRoleAssignmentService.class);
    private final RegionService regionService = mock(RegionService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final ChangeRegionAdminRoleUseCase useCase = new ChangeRegionAdminRoleUseCase(
        platformAdminAuthorizationService,
        appUserService,
        userRoleAssignmentService,
        regionService,
        contentService,
        recordAuditEventUseCase,
        Clock.fixed(CHANGED_AT, ZoneOffset.UTC)
    );

    @Test
    void 지역관리자를_새로_임명하고_성공_감사를_기록한다() {
        AppUser targetUser = ordinaryUser();
        Region requestedRegion = region(REQUESTED_REGION_ID);
        UserRoleAssignment assigned = assignment(
            targetUser,
            requestedRegion,
            300L,
            UserRoleAssignmentStatus.ACTIVE,
            null
        );
        when(assigned.getGrantedAt()).thenReturn(CHANGED_AT);
        givenAuthorizedActor();
        when(appUserService.findActiveUserForUpdate(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
        when(userRoleAssignmentService.findActiveRegionAdminForUpdate(TARGET_USER_ID))
            .thenReturn(Optional.empty());
        when(regionService.findRegionForUpdate(REQUESTED_REGION_ID)).thenReturn(requestedRegion);
        when(userRoleAssignmentService.assignRegionAdmin(targetUser, requestedRegion, CHANGED_AT))
            .thenReturn(assigned);

        RegionAdminRoleChangeResult result = change(RegionAdminRoleChange.REGION_ADMIN, REQUESTED_REGION_ID);

        assertThat(result).isEqualTo(new RegionAdminRoleChangeResult(
            TARGET_USER_ID,
            300L,
            UserRole.REGION_ADMIN,
            REQUESTED_REGION_ID,
            UserRoleAssignmentStatus.ACTIVE,
            CHANGED_AT,
            null
        ));
        verifySuccessfulAudit(
            1,
            300L,
            REQUESTED_REGION_ID,
            null,
            UserRoleAssignmentStatus.ACTIVE.name()
        );
    }

    @Test
    void 다른_지역으로_재배정하면_기존_배정을_회수하고_새_배정의_감사를_기록한다() {
        AppUser targetUser = ordinaryUser();
        Region previousRegion = region(PREVIOUS_REGION_ID);
        Region requestedRegion = region(REQUESTED_REGION_ID);
        UserRoleAssignment activeAssignment = assignment(
            targetUser,
            previousRegion,
            300L,
            UserRoleAssignmentStatus.ACTIVE,
            null
        );
        UserRoleAssignment revokedAssignment = assignment(
            targetUser,
            previousRegion,
            300L,
            UserRoleAssignmentStatus.REVOKED,
            CHANGED_AT
        );
        UserRoleAssignment assigned = assignment(
            targetUser,
            requestedRegion,
            301L,
            UserRoleAssignmentStatus.ACTIVE,
            null
        );
        givenAuthorizedActor();
        when(appUserService.findActiveUserForUpdate(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
        when(userRoleAssignmentService.findActiveRegionAdminForUpdate(TARGET_USER_ID))
            .thenReturn(Optional.of(activeAssignment));
        when(regionService.findRegionForUpdate(PREVIOUS_REGION_ID)).thenReturn(previousRegion);
        when(regionService.findRegionForUpdate(REQUESTED_REGION_ID)).thenReturn(requestedRegion);
        when(contentService.hasUndeletedContentInRegion(PREVIOUS_REGION_ID)).thenReturn(false);
        when(userRoleAssignmentService.revoke(
            activeAssignment,
            CHANGED_AT,
            "REGION_ADMIN_REASSIGNMENT"
        )).thenReturn(revokedAssignment);
        when(userRoleAssignmentService.assignRegionAdmin(targetUser, requestedRegion, CHANGED_AT))
            .thenReturn(assigned);

        RegionAdminRoleChangeResult result = change(RegionAdminRoleChange.REGION_ADMIN, REQUESTED_REGION_ID);

        assertThat(result.roleAssignmentId()).isEqualTo(301L);
        assertThat(result.regionId()).isEqualTo(REQUESTED_REGION_ID);
        verify(userRoleAssignmentService).revoke(
            activeAssignment,
            CHANGED_AT,
            "REGION_ADMIN_REASSIGNMENT"
        );
        verifySuccessfulAudit(
            2,
            301L,
            REQUESTED_REGION_ID,
            null,
            UserRoleAssignmentStatus.ACTIVE.name()
        );
    }

    @Test
    void 같은_지역으로_다시_임명하면_변경과_감사_없이_기존_배정을_반환한다() {
        AppUser targetUser = ordinaryUser();
        Region previousRegion = region(PREVIOUS_REGION_ID);
        UserRoleAssignment activeAssignment = assignment(
            targetUser,
            previousRegion,
            300L,
            UserRoleAssignmentStatus.ACTIVE,
            null
        );
        givenAuthorizedActor();
        when(appUserService.findActiveUserForUpdate(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
        when(userRoleAssignmentService.findActiveRegionAdminForUpdate(TARGET_USER_ID))
            .thenReturn(Optional.of(activeAssignment));

        RegionAdminRoleChangeResult result = change(RegionAdminRoleChange.REGION_ADMIN, PREVIOUS_REGION_ID);

        assertThat(result.roleAssignmentId()).isEqualTo(300L);
        verify(userRoleAssignmentService, never()).revoke(any(), any(), any());
        verify(userRoleAssignmentService, never()).assignRegionAdmin(any(), any(), any());
        verify(recordAuditEventUseCase, never()).record(any());
    }

    @Test
    void 지역관리자_배정이_없으면_회수를_거부한다() {
        AppUser targetUser = ordinaryUser();
        givenAuthorizedActor();
        when(appUserService.findActiveUserForUpdate(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
        when(userRoleAssignmentService.findActiveRegionAdminForUpdate(TARGET_USER_ID))
            .thenReturn(Optional.empty());

        assertRoleAssignmentConflict(() -> change(RegionAdminRoleChange.NONE, null));

        verify(userRoleAssignmentService, never()).revoke(any(), any(), any());
        verify(recordAuditEventUseCase, never()).record(any());
    }

    @Test
    void 콘텐츠가_있는_지역의_마지막_지역관리자_회수를_거부한다() {
        AppUser targetUser = ordinaryUser();
        Region previousRegion = region(PREVIOUS_REGION_ID);
        UserRoleAssignment activeAssignment = assignment(
            targetUser,
            previousRegion,
            300L,
            UserRoleAssignmentStatus.ACTIVE,
            null
        );
        givenAuthorizedActor();
        when(appUserService.findActiveUserForUpdate(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
        when(userRoleAssignmentService.findActiveRegionAdminForUpdate(TARGET_USER_ID))
            .thenReturn(Optional.of(activeAssignment));
        when(regionService.findRegionForUpdate(PREVIOUS_REGION_ID)).thenReturn(previousRegion);
        when(contentService.hasUndeletedContentInRegion(PREVIOUS_REGION_ID)).thenReturn(true);
        when(userRoleAssignmentService.countActiveRegionAdmins(PREVIOUS_REGION_ID)).thenReturn(1L);

        assertRoleAssignmentConflict(() -> change(RegionAdminRoleChange.NONE, null));

        verify(userRoleAssignmentService, never()).revoke(any(), any(), any());
        verify(recordAuditEventUseCase, never()).record(any());
    }

    @Test
    void 일반_계정이_아닌_대상은_역할_변경을_거부한다() {
        AppUser privilegedUser = mock(AppUser.class);
        when(privilegedUser.getAccountKind()).thenReturn(AppUserAccountKind.PRIVILEGED);
        givenAuthorizedActor();
        when(appUserService.findActiveUserForUpdate(TARGET_USER_ID)).thenReturn(Optional.of(privilegedUser));

        assertRoleAssignmentConflict(() -> change(RegionAdminRoleChange.NONE, null));

        verify(userRoleAssignmentService, never()).findActiveRegionAdminForUpdate(any());
    }

    private RegionAdminRoleChangeResult change(
        RegionAdminRoleChange roleChange,
        Long regionId
    ) {
        return useCase.change(
            ACTOR_USER_ID,
            TARGET_USER_ID,
            roleChange,
            regionId,
            REASON_CODE,
            EVIDENCE_REFERENCE,
            REQUEST_ID
        );
    }

    private void givenAuthorizedActor() {
        PlatformAdminAssignment actor = platformAdminActor();
        when(platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(ACTOR_USER_ID))
            .thenReturn(actor);
    }

    private PlatformAdminAssignment platformAdminActor() {
        AppUser actorUser = mock(AppUser.class);
        when(actorUser.getUserId()).thenReturn(ACTOR_USER_ID);
        when(actorUser.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(actorUser.getAccountKind()).thenReturn(AppUserAccountKind.PRIVILEGED);
        PlatformAdminAssignment actor = mock(PlatformAdminAssignment.class);
        when(actor.getPlatformAdminAssignmentId()).thenReturn(1L);
        when(actor.getAppUser()).thenReturn(actorUser);
        when(actor.getGrade()).thenReturn(PlatformAdminGrade.PLATFORM_ADMIN);
        when(actor.isActive()).thenReturn(true);
        return actor;
    }

    private AppUser ordinaryUser() {
        AppUser user = mock(AppUser.class);
        when(user.getUserId()).thenReturn(TARGET_USER_ID);
        when(user.getAccountKind()).thenReturn(AppUserAccountKind.ORDINARY);
        return user;
    }

    private Region region(Long regionId) {
        Region region = mock(Region.class);
        when(region.getRegionId()).thenReturn(regionId);
        return region;
    }

    private UserRoleAssignment assignment(
        AppUser user,
        Region region,
        Long assignmentId,
        UserRoleAssignmentStatus status,
        Instant revokedAt
    ) {
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        when(assignment.getAppUser()).thenReturn(user);
        when(assignment.getRegion()).thenReturn(region);
        when(assignment.getRoleAssignmentId()).thenReturn(assignmentId);
        when(assignment.getRole()).thenReturn(UserRole.REGION_ADMIN);
        when(assignment.getStatus()).thenReturn(status);
        when(assignment.getGrantedAt()).thenReturn(CHANGED_AT.minusSeconds(60));
        when(assignment.getRevokedAt()).thenReturn(revokedAt);
        return assignment;
    }

    private void verifySuccessfulAudit(
        int expectedCount,
        Long expectedAssignmentId,
        Long expectedRegionId,
        String expectedPreviousState,
        String expectedNextState
    ) {
        ArgumentCaptor<AuditEventCommand> commandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase, org.mockito.Mockito.times(expectedCount)).record(commandCaptor.capture());
        List<AuditEventCommand> commands = commandCaptor.getAllValues();
        AuditEventCommand command = commands.get(expectedCount - 1);
        assertThat(command.targetType()).isEqualTo(AuditEventTargetType.USER_ROLE_ASSIGNMENT);
        assertThat(command.targetId()).isEqualTo(expectedAssignmentId);
        assertThat(command.region().getRegionId()).isEqualTo(expectedRegionId);
        assertThat(command.previousState()).isEqualTo(expectedPreviousState);
        assertThat(command.nextState()).isEqualTo(expectedNextState);
        assertThat(command.reasonCode()).isEqualTo(REASON_CODE);
        assertThat(command.evidenceReference()).isEqualTo(EVIDENCE_REFERENCE);
        assertThat(command.occurredAt()).isEqualTo(CHANGED_AT);
    }

    private void assertRoleAssignmentConflict(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ROLE_ASSIGNMENT_CONFLICT)
            );
    }
}
