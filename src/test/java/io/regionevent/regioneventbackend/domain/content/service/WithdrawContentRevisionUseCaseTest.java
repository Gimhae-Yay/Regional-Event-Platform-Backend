package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentId;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class WithdrawContentRevisionUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 100L;
    private static final Long REVISION_ID = 501L;
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000294");
    private static final Instant WITHDRAWN_AT = Instant.parse("2026-08-02T10:56:14Z");

    private final ContentRevisionService contentRevisionService = mock(ContentRevisionService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final OperatorAuthorizationService operatorAuthorizationService =
        mock(OperatorAuthorizationService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final WithdrawContentRevisionUseCase useCase = new WithdrawContentRevisionUseCase(
        contentRevisionService,
        contentService,
        operatorAuthorizationService,
        recordAuditEventUseCase,
        Clock.fixed(WITHDRAWN_AT, ZoneOffset.UTC)
    );

    @Test
    void withdraw_whenRevisionIsRequested_withdrawsAndRecordsAudit() {
        Fixture fixture = fixture();
        when(fixture.revision().getStatus()).thenReturn(
            ContentRevisionStatus.EDIT_REQUESTED,
            ContentRevisionStatus.EDIT_REQUESTED,
            ContentRevisionStatus.EDIT_WITHDRAWN
        );
        stubCommon(fixture);
        when(contentRevisionService.withdraw(
            fixture.revision(),
            fixture.operator(),
            WITHDRAWN_AT,
            "withdrawal reason"
        )).thenReturn(fixture.revision());

        WithdrawContentRevisionResult result = useCase.withdraw(
            USER_ID,
            REVISION_ID,
            "withdrawal reason",
            REQUEST_ID
        );

        assertThat(result.revisionId()).isEqualTo(REVISION_ID);
        assertThat(result.contentId()).isEqualTo(CONTENT_ID);
        assertThat(result.status()).isEqualTo(ContentRevisionStatus.EDIT_WITHDRAWN);
        InOrder lockOrder = inOrder(contentRevisionService, contentService);
        lockOrder.verify(contentRevisionService).findContentIdByRevisionId(REVISION_ID);
        lockOrder.verify(contentService).findApprovalTargetForUpdate(CONTENT_ID);
        lockOrder.verify(contentRevisionService).findReviewTargetForUpdate(REVISION_ID);
        verify(contentRevisionService).withdraw(
            fixture.revision(),
            fixture.operator(),
            WITHDRAWN_AT,
            "withdrawal reason"
        );
        assertAuditCommand(fixture);
    }

    @Test
    void withdraw_whenRevisionIsAlreadyWithdrawn_returnsStoredResultWithoutAudit() {
        Fixture fixture = fixture();
        when(fixture.revision().getStatus()).thenReturn(ContentRevisionStatus.EDIT_WITHDRAWN);
        when(fixture.revision().getWithdrawalReason()).thenReturn("stored reason");
        when(fixture.revision().getWithdrawnAt()).thenReturn(WITHDRAWN_AT.minusSeconds(60));
        stubCommon(fixture);

        WithdrawContentRevisionResult result = useCase.withdraw(
            USER_ID,
            REVISION_ID,
            "different reason",
            REQUEST_ID
        );

        assertThat(result.status()).isEqualTo(ContentRevisionStatus.EDIT_WITHDRAWN);
        assertThat(result.withdrawalReason()).isEqualTo("stored reason");
        assertThat(result.withdrawnAt()).isEqualTo(WITHDRAWN_AT.minusSeconds(60));
        verify(contentRevisionService, never()).withdraw(
            fixture.revision(),
            fixture.operator(),
            WITHDRAWN_AT,
            "different reason"
        );
        verifyNoInteractions(recordAuditEventUseCase);
    }

    @Test
    void withdraw_whenRevisionIsTerminalButNotWithdrawn_throwsContentStateConflict() {
        Fixture fixture = fixture();
        when(fixture.revision().getStatus()).thenReturn(ContentRevisionStatus.EDIT_APPROVED);
        stubCommon(fixture);

        assertThatThrownBy(() -> useCase.withdraw(USER_ID, REVISION_ID, "withdrawal reason", REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
            );
        verifyNoInteractions(recordAuditEventUseCase);
    }

    @Test
    void withdraw_whenOperatorDoesNotOwnContent_throwsForbidden() {
        Fixture fixture = fixture();
        when(fixture.content().isOwnedBy(USER_ID)).thenReturn(false);
        when(fixture.content().isScopedTo(REGION_ID)).thenReturn(true);
        when(operatorAuthorizationService.requireAuthorizedOperator(USER_ID)).thenReturn(fixture.authorizedOperator());
        when(contentRevisionService.findContentIdByRevisionId(REVISION_ID)).thenReturn(CONTENT_ID);
        when(contentService.findApprovalTargetForUpdate(CONTENT_ID)).thenReturn(fixture.content());
        when(contentRevisionService.findReviewTargetForUpdate(REVISION_ID)).thenReturn(fixture.revision());

        assertThatThrownBy(() -> useCase.withdraw(USER_ID, REVISION_ID, "withdrawal reason", REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
        verifyNoInteractions(recordAuditEventUseCase);
    }

    private Fixture fixture() {
        ContentRevision revision = mock(ContentRevision.class);
        Content content = mock(Content.class);
        Region region = mock(Region.class);
        AppUser operator = mock(AppUser.class);
        UserRoleAssignment operatorAssignment = mock(UserRoleAssignment.class);
        when(revision.getContent()).thenReturn(content);
        when(revision.getContentRevisionId()).thenReturn(REVISION_ID);
        when(revision.getWithdrawalReason()).thenReturn("withdrawal reason");
        when(revision.getWithdrawnAt()).thenReturn(WITHDRAWN_AT);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(content.getRegion()).thenReturn(region);
        when(content.isOwnedBy(USER_ID)).thenReturn(true);
        when(content.isScopedTo(REGION_ID)).thenReturn(true);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(operator.getUserId()).thenReturn(USER_ID);
        when(operator.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(operatorAssignment.getAppUser()).thenReturn(operator);
        when(operatorAssignment.getId()).thenReturn(new UserRoleAssignmentId(USER_ID, UserRole.OPERATOR));
        when(operatorAssignment.getRole()).thenReturn(UserRole.OPERATOR);
        AuthorizedOperator authorizedOperator = new AuthorizedOperator(operator, region, operatorAssignment);
        return new Fixture(revision, content, region, operator, operatorAssignment, authorizedOperator);
    }

    private void stubCommon(Fixture fixture) {
        when(operatorAuthorizationService.requireAuthorizedOperator(USER_ID)).thenReturn(fixture.authorizedOperator());
        when(contentRevisionService.findContentIdByRevisionId(REVISION_ID)).thenReturn(CONTENT_ID);
        when(contentService.findApprovalTargetForUpdate(CONTENT_ID)).thenReturn(fixture.content());
        when(contentRevisionService.findReviewTargetForUpdate(REVISION_ID)).thenReturn(fixture.revision());
    }

    private void assertAuditCommand(Fixture fixture) {
        ArgumentCaptor<AuditEventCommand> commandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(commandCaptor.capture());
        AuditEventCommand command = commandCaptor.getValue();
        assertThat(command.requestId()).isEqualTo(REQUEST_ID);
        assertThat(command.region()).isEqualTo(fixture.region());
        assertThat(command.targetType()).isEqualTo(AuditEventTargetType.CONTENT);
        assertThat(command.targetId()).isEqualTo(CONTENT_ID);
        assertThat(command.previousState()).isEqualTo(ContentRevisionStatus.EDIT_REQUESTED.name());
        assertThat(command.nextState()).isEqualTo(ContentRevisionStatus.EDIT_WITHDRAWN.name());
        assertThat(command.result()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(command.reasonCode()).isNull();
        assertThat(command.actor().roleAssignment()).isEqualTo(fixture.operatorAssignment());
        assertThat(command.occurredAt()).isEqualTo(WITHDRAWN_AT);
    }

    private record Fixture(
        ContentRevision revision,
        Content content,
        Region region,
        AppUser operator,
        UserRoleAssignment operatorAssignment,
        AuthorizedOperator authorizedOperator
    ) {
    }
}
