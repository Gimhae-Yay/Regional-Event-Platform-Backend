package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentId;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class RejectContentUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 100L;
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant CLOCK_INSTANT = Instant.parse("2026-08-02T10:56:14.722444122Z");
    private static final Instant EXPECTED_REJECTED_AT = Instant.parse("2026-08-02T10:56:14.722444Z");
    private static final String REJECT_REASON = "필수 정보를 보완해 주세요.";

    private final ContentService contentService = mock(ContentService.class);
    private final OriginalContentReviewTargetService originalContentReviewTargetService =
        mock(OriginalContentReviewTargetService.class);
    private final ContentLogService contentLogService = mock(ContentLogService.class);
    private final RegionAdminAuthorizationService regionAdminAuthorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final RejectContentUseCase useCase = new RejectContentUseCase(
        contentService,
        originalContentReviewTargetService,
        contentLogService,
        regionAdminAuthorizationService,
        recordAuditEventUseCase,
        Clock.fixed(CLOCK_INSTANT, ZoneOffset.UTC)
    );

    @Test
    void 최초_반려_시각을_MySQL_TIMESTAMP_6_정밀도로_정규화한다() {
        Content pendingContent = mock(Content.class);
        Content rejectedContent = mock(Content.class);
        ContentLog pendingLog = mock(ContentLog.class);
        Region region = mock(Region.class);
        UserRoleAssignment reviewerAssignment = mock(UserRoleAssignment.class);
        AppUser reviewer = mock(AppUser.class);
        OriginalContentReviewTarget reviewTarget = new OriginalContentReviewTarget(
            pendingContent,
            pendingLog,
            null,
            OriginalContentReviewTargetType.INITIAL_SUBMISSION
        );

        when(contentService.findApprovalTargetForUpdate(CONTENT_ID)).thenReturn(pendingContent);
        when(pendingContent.getContentId()).thenReturn(CONTENT_ID);
        when(pendingContent.getRegion()).thenReturn(region);
        when(pendingContent.getStatus()).thenReturn(ContentStatus.PENDING);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(regionAdminAuthorizationService.authorize(USER_ID, REGION_ID)).thenReturn(reviewerAssignment);
        when(reviewerAssignment.getId())
            .thenReturn(new UserRoleAssignmentId(USER_ID, UserRole.REGION_ADMIN));
        when(reviewerAssignment.getAppUser()).thenReturn(reviewer);
        when(reviewer.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(originalContentReviewTargetService.findByContentId(CONTENT_ID))
            .thenReturn(Optional.of(reviewTarget));
        when(contentService.reject(pendingContent, EXPECTED_REJECTED_AT)).thenReturn(rejectedContent);
        when(rejectedContent.getContentId()).thenReturn(CONTENT_ID);
        when(rejectedContent.getRegion()).thenReturn(region);
        when(rejectedContent.getStatus()).thenReturn(ContentStatus.REJECTED);

        RejectContentResult result = useCase.reject(
            USER_ID,
            CONTENT_ID,
            "  " + REJECT_REASON + "  ",
            REQUEST_ID
        );

        assertThat(result).isEqualTo(new RejectContentResult(
            CONTENT_ID,
            ContentStatus.REJECTED,
            EXPECTED_REJECTED_AT
        ));
        verify(contentLogService).recordRejected(
            rejectedContent,
            reviewer,
            EXPECTED_REJECTED_AT,
            REJECT_REASON
        );
        ArgumentCaptor<AuditEventCommand> commandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(commandCaptor.capture());
        assertThat(commandCaptor.getValue()).satisfies(command -> {
            assertThat(command.previousState()).isEqualTo("PENDING");
            assertThat(command.nextState()).isEqualTo("REJECTED");
            assertThat(command.result()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(command.occurredAt()).isEqualTo(EXPECTED_REJECTED_AT);
        });
    }

    @Test
    void 같은_사유로_반복_반려하면_기존_결과만_반환한다() {
        Content rejectedContent = mock(Content.class);
        ContentLog rejectedLog = mock(ContentLog.class);
        Region region = mock(Region.class);
        UserRoleAssignment reviewerAssignment = mock(UserRoleAssignment.class);
        Instant rejectedAt = Instant.parse("2026-08-01T12:00:00Z");

        when(contentService.findApprovalTargetForUpdate(CONTENT_ID)).thenReturn(rejectedContent);
        when(rejectedContent.getContentId()).thenReturn(CONTENT_ID);
        when(rejectedContent.getRegion()).thenReturn(region);
        when(rejectedContent.getStatus()).thenReturn(ContentStatus.REJECTED);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(regionAdminAuthorizationService.authorize(USER_ID, REGION_ID)).thenReturn(reviewerAssignment);
        when(contentLogService.findLatestRejected(CONTENT_ID)).thenReturn(rejectedLog);
        when(rejectedLog.getReason()).thenReturn(REJECT_REASON);
        when(rejectedLog.getDate()).thenReturn(rejectedAt);

        RejectContentResult result = useCase.reject(USER_ID, CONTENT_ID, REJECT_REASON, REQUEST_ID);

        assertThat(result).isEqualTo(new RejectContentResult(
            CONTENT_ID,
            ContentStatus.REJECTED,
            rejectedAt
        ));
        verify(contentService, never()).reject(rejectedContent, rejectedAt);
        verify(contentLogService, never()).recordRejected(
            rejectedContent,
            null,
            rejectedAt,
            REJECT_REASON
        );
        verify(recordAuditEventUseCase, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 다른_사유로_반복_반려하면_상태_충돌을_반환한다() {
        Content rejectedContent = mock(Content.class);
        ContentLog rejectedLog = mock(ContentLog.class);
        Region region = mock(Region.class);
        UserRoleAssignment reviewerAssignment = mock(UserRoleAssignment.class);

        when(contentService.findApprovalTargetForUpdate(CONTENT_ID)).thenReturn(rejectedContent);
        when(rejectedContent.getContentId()).thenReturn(CONTENT_ID);
        when(rejectedContent.getRegion()).thenReturn(region);
        when(rejectedContent.getStatus()).thenReturn(ContentStatus.REJECTED);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(regionAdminAuthorizationService.authorize(USER_ID, REGION_ID)).thenReturn(reviewerAssignment);
        when(contentLogService.findLatestRejected(CONTENT_ID)).thenReturn(rejectedLog);
        when(rejectedLog.getReason()).thenReturn(REJECT_REASON);

        assertThatThrownBy(() -> useCase.reject(USER_ID, CONTENT_ID, "다른 사유", REQUEST_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT);
        verify(contentService, never()).reject(rejectedContent, CLOCK_INSTANT);
        verify(recordAuditEventUseCase, never()).record(org.mockito.ArgumentMatchers.any());
    }
}
