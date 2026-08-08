package io.regionevent.regioneventbackend.domain.content.service;

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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentSessionRequest;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.service.SessionRevisionService.CreateSessionRevisionCommand;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class CreateSessionRevisionUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long CONTENT_ID = 10L;
    private static final Long SESSION_ID = 21L;
    private static final Long REGION_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");
    private static final Instant STARTS_AT = NOW.plusSeconds(86_400);
    private static final Instant SUBMITTED_AT = NOW.plusSeconds(1).plusNanos(789);
    private static final Instant NORMALIZED_SUBMITTED_AT = SUBMITTED_AT.truncatedTo(ChronoUnit.MICROS);

    private final OperatorAuthorizationService operatorAuthorizationService = mock(
        OperatorAuthorizationService.class
    );
    private final ContentService contentService = mock(ContentService.class);
    private final ContentSessionService contentSessionService = mock(ContentSessionService.class);
    private final SessionRevisionService sessionRevisionService = mock(SessionRevisionService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final Clock clock = mock(Clock.class);
    private final CreateSessionRevisionUseCase useCase = new CreateSessionRevisionUseCase(
        operatorAuthorizationService,
        contentService,
        contentSessionService,
        sessionRevisionService,
        recordAuditEventUseCase,
        clock
    );

    @Test
    void create_유효한_요청이면_기존_회차를_변경하지_않고_심사대기_수정_요청과_감사를_생성한다() {
        AuthorizedOperator operator = authorizedOperator();
        Content content = mock(Content.class);
        ContentSession targetSession = mock(ContentSession.class);
        SessionRevision revision = mock(SessionRevision.class);
        UUID requestId = UUID.randomUUID();
        CreateContentSessionRequest request = request();

        when(clock.instant()).thenReturn(NOW, SUBMITTED_AT);
        when(contentSessionService.findContentIdBySessionId(SESSION_ID)).thenReturn(CONTENT_ID);
        when(operatorAuthorizationService.requireAuthorizedOperator(USER_ID)).thenReturn(operator);
        when(contentService.findOwnedContentForRevisionCreation(CONTENT_ID, USER_ID, REGION_ID))
            .thenReturn(content);
        when(contentSessionService.findRevisionTargetForUpdate(SESSION_ID)).thenReturn(targetSession);
        when(content.getStatus()).thenReturn(ContentStatus.PUBLISHED);
        when(content.getPublishAt()).thenReturn(NOW.minusSeconds(60));
        when(content.getRegion()).thenReturn(operator.region());
        when(targetSession.getSessionId()).thenReturn(SESSION_ID);
        when(targetSession.getStatus()).thenReturn(ContentSessionStatus.SCHEDULED);
        when(contentSessionService.isBeforeStartByDatabaseTime(SESSION_ID)).thenReturn(true);
        when(sessionRevisionService.createPending(
            eq(targetSession),
            eq(operator.user()),
            any(CreateSessionRevisionCommand.class),
            eq(NORMALIZED_SUBMITTED_AT)
        )).thenReturn(revision);
        stubRevision(revision, content, targetSession);

        CreateSessionRevisionResult result = useCase.create(USER_ID, SESSION_ID, request, requestId);

        assertThat(result.revisionId()).isEqualTo(52L);
        assertThat(result.baseSessionVersion()).isEqualTo(3);
        ArgumentCaptor<CreateSessionRevisionCommand> commandCaptor = ArgumentCaptor.forClass(
            CreateSessionRevisionCommand.class
        );
        verify(sessionRevisionService).createPending(
            eq(targetSession),
            eq(operator.user()),
            commandCaptor.capture(),
            eq(NORMALIZED_SUBMITTED_AT)
        );
        assertThat(commandCaptor.getValue().startsAt()).isEqualTo(STARTS_AT);
        assertThat(commandCaptor.getValue().capacity()).isEqualTo(30);

        ArgumentCaptor<AuditEventCommand> auditCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().requestId()).isEqualTo(requestId);
        assertThat(auditCaptor.getValue().targetType()).isEqualTo(AuditEventTargetType.CONTENT_SESSION);
        assertThat(auditCaptor.getValue().targetId()).isEqualTo(SESSION_ID);
        assertThat(auditCaptor.getValue().nextState()).isEqualTo(SessionRevisionStatus.PENDING.name());
        assertThat(auditCaptor.getValue().result()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(auditCaptor.getValue().occurredAt()).isEqualTo(NORMALIZED_SUBMITTED_AT);
    }

    @Test
    void create_대상_회차가_예정상태가_아니면_수정요청과_감사를_생성하지_않는다() {
        AuthorizedOperator operator = authorizedOperator();
        Content content = mock(Content.class);
        ContentSession targetSession = mock(ContentSession.class);

        when(clock.instant()).thenReturn(NOW);
        when(contentSessionService.findContentIdBySessionId(SESSION_ID)).thenReturn(CONTENT_ID);
        when(operatorAuthorizationService.requireAuthorizedOperator(USER_ID)).thenReturn(operator);
        when(contentService.findOwnedContentForRevisionCreation(CONTENT_ID, USER_ID, REGION_ID))
            .thenReturn(content);
        when(contentSessionService.findRevisionTargetForUpdate(SESSION_ID)).thenReturn(targetSession);
        when(content.getStatus()).thenReturn(ContentStatus.PUBLISHED);
        when(targetSession.getStatus()).thenReturn(ContentSessionStatus.CANCELLED);

        assertThatThrownBy(() -> useCase.create(USER_ID, SESSION_ID, request(), UUID.randomUUID()))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SESSION_STATE_CONFLICT)
            );

        verify(sessionRevisionService, never()).createPending(any(), any(), any(), any());
        verify(recordAuditEventUseCase, never()).record(any());
    }

    private AuthorizedOperator authorizedOperator() {
        AppUser user = mock(AppUser.class);
        Region region = mock(Region.class);
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        Long assignmentId = 1L;
        when(user.getUserId()).thenReturn(USER_ID);
        when(user.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(assignment.getRoleAssignmentId()).thenReturn(assignmentId);
        when(assignment.getAppUser()).thenReturn(user);
        return new AuthorizedOperator(user, region, assignment);
    }

    private void stubRevision(
        SessionRevision revision,
        Content content,
        ContentSession targetSession
    ) {
        when(revision.getSessionRevisionId()).thenReturn(52L);
        when(revision.getStatus()).thenReturn(SessionRevisionStatus.PENDING);
        when(revision.getContent()).thenReturn(content);
        when(revision.getTargetSession()).thenReturn(targetSession);
        when(revision.getBaseSessionVersion()).thenReturn(3);
        when(revision.getStartsAt()).thenReturn(STARTS_AT);
        when(revision.getEndsAt()).thenReturn(STARTS_AT.plusSeconds(7_200));
        when(revision.getCheckinOpenAt()).thenReturn(STARTS_AT.minusSeconds(1_800));
        when(revision.getCheckinCloseAt()).thenReturn(STARTS_AT.plusSeconds(5_400));
        when(revision.getCapacity()).thenReturn(30);
        when(revision.getSubmittedAt()).thenReturn(NORMALIZED_SUBMITTED_AT);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(targetSession.getSessionId()).thenReturn(SESSION_ID);
    }

    private CreateContentSessionRequest request() {
        return new CreateContentSessionRequest(
            OffsetDateTime.ofInstant(STARTS_AT, ZoneOffset.ofHours(9)),
            OffsetDateTime.ofInstant(STARTS_AT.plusSeconds(7_200), ZoneOffset.ofHours(9)),
            OffsetDateTime.ofInstant(STARTS_AT.minusSeconds(1_800), ZoneOffset.ofHours(9)),
            OffsetDateTime.ofInstant(STARTS_AT.plusSeconds(5_400), ZoneOffset.ofHours(9)),
            30
        );
    }
}
