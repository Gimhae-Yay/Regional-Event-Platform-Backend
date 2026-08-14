package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;

class ApproveContentRevisionUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 100L;
    private static final Long REVISION_ID = 501L;
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000215");
    private static final Instant CLOCK_INSTANT = Instant.parse("2026-08-02T10:56:14.722444122Z");
    private static final Instant REVIEWED_AT = Instant.parse("2026-08-02T10:56:14.722444Z");
    private static final Instant PUBLISH_AT = Instant.parse("2026-08-20T00:00:00Z");
    private static final long RESERVATION_PRICE = 20_000L;

    private final ContentRevisionService contentRevisionService = mock(ContentRevisionService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final OriginalContentReviewTargetService originalContentReviewTargetService =
        mock(OriginalContentReviewTargetService.class);
    private final ContentLogService contentLogService = mock(ContentLogService.class);
    private final RegionAdminAuthorizationService regionAdminAuthorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final ApproveContentRevisionUseCase useCase = new ApproveContentRevisionUseCase(
        contentRevisionService,
        contentService,
        originalContentReviewTargetService,
        contentLogService,
        regionAdminAuthorizationService,
        recordAuditEventUseCase,
        Clock.fixed(CLOCK_INSTANT, ZoneOffset.UTC)
    );

    @Test
    void approve_whenPublishedRevision_doesNotRecordContentStatusLog() {
        Fixture fixture = fixture(ContentStatus.PUBLISHED);
        stubCommon(fixture, false);

        ApproveContentRevisionResult result = useCase.approve(USER_ID, REVISION_ID, REQUEST_ID);

        assertThat(result.revisionStatus()).isEqualTo(ContentRevisionStatus.EDIT_APPROVED);
        assertThat(result.contentStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(result.reservationPrice()).isEqualTo(RESERVATION_PRICE);
        assertThat(result.reviewedAt()).isEqualTo(REVIEWED_AT);
        InOrder lockOrder = inOrder(contentRevisionService, contentService);
        lockOrder.verify(contentRevisionService).findContentIdByRevisionId(REVISION_ID);
        lockOrder.verify(contentService).findApprovalTargetForUpdate(CONTENT_ID);
        lockOrder.verify(contentRevisionService).findReviewTargetForUpdate(REVISION_ID);
        verifyNoInteractions(originalContentReviewTargetService);
        verify(contentLogService, never()).recordApproved(fixture.content(), fixture.reviewer(), REVIEWED_AT);
        assertAuditCommand(fixture);
    }

    @Test
    void approve_whenPrePublicationRevision_recordsApprovedLogInSameFlow() {
        Fixture fixture = fixture(ContentStatus.PENDING);
        OriginalContentReviewTarget target = new OriginalContentReviewTarget(
            fixture.content(),
            null,
            null,
            OriginalContentReviewTargetType.PRE_PUBLICATION_REVISION
        );
        when(originalContentReviewTargetService.findByContentId(CONTENT_ID))
            .thenReturn(Optional.of(target));
        stubCommon(fixture, true);

        ApproveContentRevisionResult result = useCase.approve(USER_ID, REVISION_ID, REQUEST_ID);

        assertThat(result.contentStatus()).isEqualTo(ContentStatus.APPROVED);
        verify(contentLogService).recordApproved(fixture.content(), fixture.reviewer(), REVIEWED_AT);
        assertAuditCommand(fixture);
    }

    private Fixture fixture(ContentStatus initialStatus) {
        ContentRevision revision = mock(ContentRevision.class);
        Content content = mock(Content.class);
        Region region = mock(Region.class);
        AppUser reviewer = mock(AppUser.class);
        UserRoleAssignment reviewerAssignment = mock(UserRoleAssignment.class);
        when(revision.getContent()).thenReturn(content);
        when(revision.getContentRevisionId()).thenReturn(REVISION_ID);
        when(revision.getStatus()).thenReturn(ContentRevisionStatus.EDIT_APPROVED);
        when(revision.getReviewedAt()).thenReturn(REVIEWED_AT);
        when(content.getRegion()).thenReturn(region);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(content.getStatus()).thenReturn(
            initialStatus,
            initialStatus,
            approvedStatus(initialStatus)
        );
        when(content.getPublishAt()).thenReturn(PUBLISH_AT);
        when(content.getReservationPrice()).thenReturn(RESERVATION_PRICE);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(reviewerAssignment.getAppUser()).thenReturn(reviewer);
        when(reviewerAssignment.getRoleAssignmentId())
            .thenReturn(1L);
        when(reviewer.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        return new Fixture(revision, content, reviewer, reviewerAssignment);
    }

    private void stubCommon(Fixture fixture, boolean isPrePublicationRevisionByHistory) {
        when(contentRevisionService.findContentIdByRevisionId(REVISION_ID))
            .thenReturn(CONTENT_ID);
        when(contentService.findApprovalTargetForUpdate(CONTENT_ID))
            .thenReturn(fixture.content());
        when(contentRevisionService.findReviewTargetForUpdate(REVISION_ID))
            .thenReturn(fixture.revision());
        givenAuthorizedRegionAdmin(fixture.reviewerAssignment());
        when(contentRevisionService.approve(
            fixture.revision(),
            fixture.reviewer(),
            REVIEWED_AT,
            isPrePublicationRevisionByHistory
        )).thenReturn(fixture.revision());
    }

    private ContentStatus approvedStatus(ContentStatus initialStatus) {
        return initialStatus == ContentStatus.PENDING
            ? ContentStatus.APPROVED
            : initialStatus;
    }

    private void assertAuditCommand(Fixture fixture) {
        ArgumentCaptor<AuditEventCommand> commandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(commandCaptor.capture());
        AuditEventCommand command = commandCaptor.getValue();
        assertThat(command.requestId()).isEqualTo(REQUEST_ID);
        assertThat(command.targetId()).isEqualTo(CONTENT_ID);
        assertThat(command.previousState()).isEqualTo(ContentRevisionStatus.EDIT_REQUESTED.name());
        assertThat(command.nextState()).isEqualTo(ContentRevisionStatus.EDIT_APPROVED.name());
        assertThat(command.occurredAt()).isEqualTo(REVIEWED_AT);
        assertThat(command.actor().roleAssignment()).isEqualTo(fixture.reviewerAssignment());
    }

    private record Fixture(
        ContentRevision revision,
        Content content,
        AppUser reviewer,
        UserRoleAssignment reviewerAssignment
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
