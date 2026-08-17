package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class ApproveContentSessionUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 100L;
    private static final Long SESSION_ID = 200L;
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant CLOCK_INSTANT = Instant.parse("2026-08-02T10:56:14.722444122Z");
    private static final Instant EXPECTED_REVIEWED_AT = Instant.parse("2026-08-02T10:56:14.722444Z");

    private final ContentService contentService = mock(ContentService.class);
    private final ContentSessionService contentSessionService = mock(ContentSessionService.class);
    private final RegionAdminAuthorizationService regionAdminAuthorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final ApproveContentSessionUseCase useCase = new ApproveContentSessionUseCase(
        contentService,
        contentSessionService,
        regionAdminAuthorizationService,
        recordAuditEventUseCase,
        Clock.fixed(CLOCK_INSTANT, ZoneOffset.UTC)
    );

    @Test
    void 승인_조건을_충족하면_회차와_성공_감사를_같은_시각으로_처리한다() {
        Fixture fixture = fixture(ContentStatus.APPROVED, ContentSessionStatus.PENDING);

        ApproveContentSessionResult result = useCase.approve(USER_ID, SESSION_ID, REQUEST_ID);

        assertThat(result).isEqualTo(new ApproveContentSessionResult(
            SESSION_ID,
            CONTENT_ID,
            ContentSessionStatus.SCHEDULED,
            EXPECTED_REVIEWED_AT
        ));
        verify(contentSessionService).approve(fixture.session(), fixture.reviewer(), EXPECTED_REVIEWED_AT);
        ArgumentCaptor<AuditEventCommand> commandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(commandCaptor.capture());
        AuditEventCommand command = commandCaptor.getValue();
        assertThat(command.targetType()).isEqualTo(AuditEventTargetType.CONTENT_SESSION);
        assertThat(command.targetId()).isEqualTo(SESSION_ID);
        assertThat(command.previousState()).isEqualTo(ContentSessionStatus.PENDING.name());
        assertThat(command.nextState()).isEqualTo(ContentSessionStatus.SCHEDULED.name());
        assertThat(command.result()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(command.occurredAt()).isEqualTo(EXPECTED_REVIEWED_AT);
    }

    @Test
    void 콘텐츠_또는_회차_상태가_승인_조건과_다르면_상태_충돌로_거부한다() {
        Fixture fixture = fixture(ContentStatus.PENDING, ContentSessionStatus.PENDING);

        assertThatThrownBy(() -> useCase.approve(USER_ID, SESSION_ID, REQUEST_ID))
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SESSION_STATE_CONFLICT));

        verify(contentSessionService, never()).approve(any(), any(), any());
        verify(recordAuditEventUseCase, never()).record(any());
        assertThat(fixture.session()).isNotNull();
    }

    @Test
    void 회차가_PENDING이_아니면_상태_충돌로_거부한다() {
        fixture(ContentStatus.PUBLISHED, ContentSessionStatus.SCHEDULED);

        assertThatThrownBy(() -> useCase.approve(USER_ID, SESSION_ID, REQUEST_ID))
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SESSION_STATE_CONFLICT));

        verify(contentSessionService, never()).approve(any(), any(), any());
        verify(recordAuditEventUseCase, never()).record(any());
    }

    private Fixture fixture(
        ContentStatus contentStatus,
        ContentSessionStatus sessionStatus
    ) {
        Content content = mock(Content.class);
        ContentSession session = mock(ContentSession.class);
        Region region = mock(Region.class);
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        AppUser reviewer = mock(AppUser.class);
        when(contentSessionService.findContentIdBySessionId(SESSION_ID)).thenReturn(CONTENT_ID);
        when(contentService.findApprovalTargetForUpdate(CONTENT_ID)).thenReturn(content);
        when(contentSessionService.findApprovalTargetForUpdate(SESSION_ID)).thenReturn(session);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(content.getRegion()).thenReturn(region);
        when(content.getStatus()).thenReturn(contentStatus);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(session.getContent()).thenReturn(content);
        when(session.getSessionId()).thenReturn(SESSION_ID);
        when(session.getStatus()).thenReturn(sessionStatus);
        givenAuthorizedRegionAdmin(assignment);
        when(assignment.getRoleAssignmentId()).thenReturn(1L);
        when(assignment.getAppUser()).thenReturn(reviewer);
        when(reviewer.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(contentSessionService.approve(session, reviewer, EXPECTED_REVIEWED_AT)).thenReturn(session);
        when(session.getStatus()).thenReturn(sessionStatus, ContentSessionStatus.SCHEDULED);
        when(session.getReviewedAt()).thenReturn(EXPECTED_REVIEWED_AT);
        return new Fixture(session, reviewer);
    }

    private record Fixture(
        ContentSession session,
        AppUser reviewer
    ) {
    }

    private void givenAuthorizedRegionAdmin(UserRoleAssignment assignment) {
        RegionAdminAuthorizationService.AuthorizedRegionAdmin regionAdmin = mock(
            RegionAdminAuthorizationService.AuthorizedRegionAdmin.class
        );
        when(regionAdminAuthorizationService.requireAuthorizedRegionAdminForUpdate(USER_ID))
            .thenReturn(regionAdmin);
        when(regionAdmin.authorize(REGION_ID)).thenReturn(assignment);
    }
}
