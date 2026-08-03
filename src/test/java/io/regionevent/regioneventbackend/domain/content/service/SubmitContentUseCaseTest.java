package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentId;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;

class SubmitContentUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 100L;
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000293");
    private static final Instant CLOCK_INSTANT = Instant.parse("2026-08-02T10:56:14.722444122Z");
    private static final Instant EXPECTED_SUBMITTED_AT = Instant.parse("2026-08-02T10:56:14.722444Z");

    private final ContentService contentService = mock(ContentService.class);
    private final ContentSessionService contentSessionService = mock(ContentSessionService.class);
    private final ContentLogService contentLogService = mock(ContentLogService.class);
    private final OperatorAuthorizationService operatorAuthorizationService =
        mock(OperatorAuthorizationService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase =
        mock(RecordFailedAuditEventUseCase.class);
    private final SubmitContentUseCase useCase = new SubmitContentUseCase(
        contentService,
        contentSessionService,
        contentLogService,
        operatorAuthorizationService,
        recordAuditEventUseCase,
        recordFailedAuditEventUseCase,
        Clock.fixed(CLOCK_INSTANT, ZoneOffset.UTC)
    );

    @Test
    void submit_whenRejectedContentIsValid_recordsPendingLogAndAudit() {
        Content rejectedContent = mock(Content.class);
        Content submittedContent = mock(Content.class);
        Region region = mock(Region.class);
        AppUser operator = mock(AppUser.class);
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);

        when(operator.getUserId()).thenReturn(USER_ID);
        when(operator.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(assignment.getId()).thenReturn(new UserRoleAssignmentId(USER_ID, UserRole.OPERATOR));
        when(assignment.getAppUser()).thenReturn(operator);
        AuthorizedOperator authorizedOperator = new AuthorizedOperator(operator, region, assignment);
        when(operatorAuthorizationService.requireAuthorizedOperator(USER_ID)).thenReturn(authorizedOperator);
        when(contentService.findRejectedOwnedContentForUpdate(CONTENT_ID, USER_ID, REGION_ID))
            .thenReturn(rejectedContent);
        when(contentService.submitForReview(rejectedContent, EXPECTED_SUBMITTED_AT)).thenReturn(submittedContent);
        when(submittedContent.getContentId()).thenReturn(CONTENT_ID);
        when(submittedContent.getStatus()).thenReturn(ContentStatus.PENDING);
        when(submittedContent.getRegion()).thenReturn(region);

        SubmitContentResult result = useCase.submit(USER_ID, CONTENT_ID, REQUEST_ID);

        assertThat(result).isEqualTo(new SubmitContentResult(
            CONTENT_ID,
            ContentStatus.PENDING,
            EXPECTED_SUBMITTED_AT
        ));
        verify(contentService).validateSubmitRequirements(rejectedContent);
        verify(contentSessionService).validatePendingSessionExists(CONTENT_ID);
        verify(contentLogService).recordPending(submittedContent, operator, EXPECTED_SUBMITTED_AT);
        ArgumentCaptor<AuditEventCommand> auditCommandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(auditCommandCaptor.capture());
        AuditEventCommand auditCommand = auditCommandCaptor.getValue();
        assertThat(auditCommand.requestId()).isEqualTo(REQUEST_ID);
        assertThat(auditCommand.region()).isEqualTo(region);
        assertThat(auditCommand.targetId()).isEqualTo(CONTENT_ID);
        assertThat(auditCommand.previousState()).isEqualTo("REJECTED");
        assertThat(auditCommand.nextState()).isEqualTo("PENDING");
        assertThat(auditCommand.occurredAt()).isEqualTo(EXPECTED_SUBMITTED_AT);
    }
}
