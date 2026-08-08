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

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class RejectSessionRevisionUseCaseTest {

    private static final long USER_ID = 100L;
    private static final long REVISION_ID = 52L;
    private static final long REGION_ID = 10L;
    private static final long CONTENT_ID = 20L;
    private static final long SESSION_ID = 30L;
    private static final Instant REVIEWED_AT = Instant.parse("2026-08-05T01:00:00Z");

    private final SessionRevisionService sessionRevisionService = mock(SessionRevisionService.class);
    private final RegionAdminAuthorizationService regionAdminAuthorizationService = mock(RegionAdminAuthorizationService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final RejectSessionRevisionUseCase useCase = new RejectSessionRevisionUseCase(
        sessionRevisionService,
        regionAdminAuthorizationService,
        recordAuditEventUseCase,
        Clock.fixed(REVIEWED_AT, ZoneOffset.UTC)
    );

    @Test
    void 반려_대상과_담당_지역이_일치하면_수정_요청만_종결하고_감사를_기록한다() {
        SessionRevision revision = revision();
        UserRoleAssignment assignment = reviewerAssignment();
        UUID requestId = UUID.randomUUID();
        when(sessionRevisionService.findReviewTargetForUpdate(REVISION_ID)).thenReturn(revision);
        when(regionAdminAuthorizationService.authorize(USER_ID, REGION_ID)).thenReturn(assignment);

        RejectSessionRevisionResult result = useCase.reject(
            USER_ID,
            REVISION_ID,
            "  정원 변경 사유를 보완해 주세요.  ",
            requestId
        );

        assertThat(result).isEqualTo(new RejectSessionRevisionResult(
            REVISION_ID,
            SessionRevisionStatus.REJECTED,
            CONTENT_ID,
            SESSION_ID,
            "정원 변경 사유를 보완해 주세요.",
            REVIEWED_AT
        ));
        verify(sessionRevisionService).rejectPending(
            REVISION_ID,
            assignment.getAppUser(),
            REVIEWED_AT,
            "정원 변경 사유를 보완해 주세요."
        );
        ArgumentCaptor<AuditEventCommand> commandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(commandCaptor.capture());
        AuditEventCommand command = commandCaptor.getValue();
        assertThat(command.requestId()).isEqualTo(requestId);
        assertThat(command.targetType()).isEqualTo(AuditEventTargetType.CONTENT_SESSION);
        assertThat(command.targetId()).isEqualTo(SESSION_ID);
        assertThat(command.previousState()).isEqualTo(SessionRevisionStatus.PENDING.name());
        assertThat(command.nextState()).isEqualTo(SessionRevisionStatus.REJECTED.name());
    }

    @Test
    void 반려_사유가_비어_있으면_상태를_변경하지_않는다() {
        assertThatThrownBy(() -> useCase.reject(USER_ID, REVISION_ID, "  ", UUID.randomUUID()))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );

        verify(sessionRevisionService, never()).findReviewTargetForUpdate(any());
        verify(sessionRevisionService, never()).rejectPending(any(), any(), any(), any());
        verify(recordAuditEventUseCase, never()).record(any());
    }

    @Test
    void 이미_종결된_요청이면_충돌을_반환하고_감사를_기록하지_않는다() {
        SessionRevision revision = revision();
        UserRoleAssignment assignment = reviewerAssignment();
        when(sessionRevisionService.findReviewTargetForUpdate(REVISION_ID)).thenReturn(revision);
        when(regionAdminAuthorizationService.authorize(USER_ID, REGION_ID)).thenReturn(assignment);
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.SESSION_STATE_CONFLICT))
            .when(sessionRevisionService)
            .rejectPending(any(), any(), any(), any());

        assertThatThrownBy(() -> useCase.reject(USER_ID, REVISION_ID, "반려 사유", UUID.randomUUID()))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SESSION_STATE_CONFLICT)
            );

        verify(recordAuditEventUseCase, never()).record(any());
    }

    private SessionRevision revision() {
        SessionRevision revision = mock(SessionRevision.class);
        Region region = mock(Region.class);
        Content content = mock(Content.class);
        ContentSession targetSession = mock(ContentSession.class);
        when(revision.getSessionRevisionId()).thenReturn(REVISION_ID);
        when(revision.getRegion()).thenReturn(region);
        when(revision.getContent()).thenReturn(content);
        when(revision.getTargetSession()).thenReturn(targetSession);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(targetSession.getSessionId()).thenReturn(SESSION_ID);
        return revision;
    }

    private UserRoleAssignment reviewerAssignment() {
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        AppUser reviewer = mock(AppUser.class);
        when(assignment.getRoleAssignmentId()).thenReturn(1L);
        when(assignment.getAppUser()).thenReturn(reviewer);
        when(reviewer.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        return assignment;
    }
}
