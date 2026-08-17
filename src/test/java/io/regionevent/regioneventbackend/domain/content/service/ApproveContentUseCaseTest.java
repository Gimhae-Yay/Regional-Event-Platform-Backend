package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;

class ApproveContentUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 100L;
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant CLOCK_INSTANT = Instant.parse("2026-08-02T10:56:14.722444122Z");
    private static final Instant EXPECTED_APPROVED_AT = Instant.parse("2026-08-02T10:56:14.722444Z");

    private final ContentService contentService = mock(ContentService.class);
    private final OriginalContentReviewTargetService originalContentReviewTargetService =
        mock(OriginalContentReviewTargetService.class);
    private final ContentSessionService contentSessionService = mock(ContentSessionService.class);
    private final ContentLogService contentLogService = mock(ContentLogService.class);
    private final RegionAdminAuthorizationService regionAdminAuthorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final ApproveContentUseCase useCase = new ApproveContentUseCase(
        contentService,
        originalContentReviewTargetService,
        contentSessionService,
        contentLogService,
        regionAdminAuthorizationService,
        recordAuditEventUseCase,
        Clock.fixed(CLOCK_INSTANT, ZoneOffset.UTC)
    );

    @Test
    void 최초_승인_시각을_MySQL_TIMESTAMP_6_정밀도로_정규화한다() {
        Content pendingContent = mock(Content.class);
        Content approvedContent = mock(Content.class);
        ContentLog pendingLog = mock(ContentLog.class);
        ContentSession session = mock(ContentSession.class);
        Region region = mock(Region.class);
        UserRoleAssignment reviewerAssignment = mock(UserRoleAssignment.class);
        AppUser reviewer = mock(AppUser.class);
        List<ContentSession> sessions = List.of(session);
        OriginalContentReviewTarget reviewTarget = new OriginalContentReviewTarget(
            pendingContent,
            pendingLog,
            null,
            OriginalContentReviewTargetType.INITIAL_SUBMISSION
        );

        when(contentService.findApprovalTargetForUpdate(CONTENT_ID)).thenReturn(pendingContent);
        when(pendingContent.getRegion()).thenReturn(region);
        when(pendingContent.getContentId()).thenReturn(CONTENT_ID);
        when(pendingContent.getStatus()).thenReturn(ContentStatus.PENDING);
        when(region.getRegionId()).thenReturn(REGION_ID);
        givenAuthorizedRegionAdmin(reviewerAssignment);
        when(reviewerAssignment.getRoleAssignmentId())
            .thenReturn(1L);
        when(reviewerAssignment.getAppUser()).thenReturn(reviewer);
        when(reviewer.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(originalContentReviewTargetService.findByContentId(CONTENT_ID))
            .thenReturn(Optional.of(reviewTarget));
        when(contentSessionService.findApprovalTargetsForUpdate(CONTENT_ID)).thenReturn(sessions);
        when(contentService.approve(pendingContent)).thenReturn(approvedContent);
        when(approvedContent.getContentId()).thenReturn(CONTENT_ID);
        when(approvedContent.getStatus()).thenReturn(ContentStatus.APPROVED);

        ApproveContentResult result = useCase.approve(USER_ID, CONTENT_ID, REQUEST_ID);

        assertThat(result.approvedAt()).isEqualTo(EXPECTED_APPROVED_AT);
        verify(contentSessionService).approveAll(sessions, reviewer, EXPECTED_APPROVED_AT);
        verify(contentLogService).recordApproved(approvedContent, reviewer, EXPECTED_APPROVED_AT);
        ArgumentCaptor<AuditEventCommand> auditCommandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(auditCommandCaptor.capture());
        assertThat(auditCommandCaptor.getValue().occurredAt()).isEqualTo(EXPECTED_APPROVED_AT);
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
