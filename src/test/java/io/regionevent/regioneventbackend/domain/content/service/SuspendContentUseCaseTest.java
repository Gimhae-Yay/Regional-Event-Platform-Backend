package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestInvalidationReason;
import io.regionevent.regioneventbackend.domain.payment.service.ExpirePendingPaymentForTerminatedHoldUseCase;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;

class SuspendContentUseCaseTest {

    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 20L;
    private static final Long ADMIN_ID = 30L;
    private static final Instant SUSPENDED_AT = Instant.parse("2026-08-16T04:00:00Z");

    private final ContentService contentService = mock(ContentService.class);
    private final ContentWithdrawalRequestService withdrawalRequestService =
        mock(ContentWithdrawalRequestService.class);
    private final ContentRevisionInvalidationService revisionInvalidationService =
        mock(ContentRevisionInvalidationService.class);
    private final ContentSessionService contentSessionService = mock(ContentSessionService.class);
    private final ContentLogService contentLogService = mock(ContentLogService.class);
    private final CapacityHoldService capacityHoldService = mock(CapacityHoldService.class);
    private final ExpirePendingPaymentForTerminatedHoldUseCase expirePaymentUseCase =
        mock(ExpirePendingPaymentForTerminatedHoldUseCase.class);
    private final RegionAdminAuthorizationService authorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final RegionService regionService = mock(RegionService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase =
        mock(RecordFailedAuditEventUseCase.class);
    private final SuspendContentUseCase useCase = new SuspendContentUseCase(
        contentService,
        withdrawalRequestService,
        revisionInvalidationService,
        contentSessionService,
        contentLogService,
        capacityHoldService,
        expirePaymentUseCase,
        authorizationService,
        regionService,
        recordAuditEventUseCase,
        recordFailedAuditEventUseCase,
        Clock.fixed(SUSPENDED_AT, ZoneOffset.UTC)
    );

    @Test
    void 중단은_수정본보다_먼저_대기_철회_요청을_관리자_actor로_무효화하고_감사한다() {
        Region region = mock(Region.class);
        AppUser admin = mock(AppUser.class);
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        Content content = mock(Content.class);
        ContentLog contentLog = mock(ContentLog.class);
        ContentWithdrawalRequest request = mock(ContentWithdrawalRequest.class);
        RegionAdminAuthorizationService.AuthorizedRegionAdmin authorizedAdmin = mock(
            RegionAdminAuthorizationService.AuthorizedRegionAdmin.class
        );
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(admin.getUserId()).thenReturn(ADMIN_ID);
        when(admin.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(assignment.getRoleAssignmentId()).thenReturn(40L);
        when(assignment.getAppUser()).thenReturn(admin);
        when(authorizationService.requireAuthorizedRegionAdminForUpdate(ADMIN_ID))
            .thenReturn(authorizedAdmin);
        when(authorizedAdmin.authorize(REGION_ID)).thenReturn(assignment);
        when(contentService.findContentRegionId(CONTENT_ID)).thenReturn(REGION_ID);
        when(regionService.findRegionForUpdate(REGION_ID)).thenReturn(region);
        when(contentService.findSuspendTargetForUpdate(CONTENT_ID)).thenReturn(content);
        when(content.getStatus()).thenReturn(ContentStatus.PUBLISHED);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(content.getRegion()).thenReturn(region);
        when(contentService.suspend(content, SUSPENDED_AT)).thenReturn(content);
        when(request.getContentWithdrawalRequestId()).thenReturn(7001L);
        when(withdrawalRequestService.invalidatePendingByUser(
            CONTENT_ID,
            admin,
            SUSPENDED_AT,
            ContentWithdrawalRequestInvalidationReason.CONTENT_SUSPENDED
        )).thenReturn(Optional.of(request));
        when(revisionInvalidationService.invalidateActiveRevisionForContent(
            eq(CONTENT_ID),
            eq(admin),
            eq(SUSPENDED_AT),
            any()
        )).thenReturn(Optional.empty());
        when(contentLogService.recordSuspended(
            content,
            admin,
            SUSPENDED_AT,
            "운영 중단"
        )).thenReturn(contentLog);
        when(capacityHoldService.invalidateAllActiveHoldsForContent(
            CONTENT_ID,
            "CONTENT_SUSPENDED"
        )).thenReturn(List.of());

        useCase.suspend(ADMIN_ID, CONTENT_ID, "운영 중단", UUID.randomUUID());

        InOrder invalidationOrder = inOrder(withdrawalRequestService, revisionInvalidationService);
        invalidationOrder.verify(withdrawalRequestService).invalidatePendingByUser(
            CONTENT_ID,
            admin,
            SUSPENDED_AT,
            ContentWithdrawalRequestInvalidationReason.CONTENT_SUSPENDED
        );
        invalidationOrder.verify(revisionInvalidationService).invalidateActiveRevisionForContent(
            eq(CONTENT_ID),
            eq(admin),
            eq(SUSPENDED_AT),
            any()
        );
        ArgumentCaptor<AuditEventCommand> commandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase, times(2)).record(commandCaptor.capture());
        AuditEventCommand requestAudit = commandCaptor.getAllValues().stream()
            .filter(command -> command.targetType() == AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST)
            .findFirst()
            .orElseThrow();
        assertThat(requestAudit.targetId()).isEqualTo(7001L);
        assertThat(requestAudit.reasonCode()).isEqualTo("CONTENT_SUSPENDED");
        assertThat(requestAudit.actor().getAppUser()).isSameAs(admin);
    }
}
