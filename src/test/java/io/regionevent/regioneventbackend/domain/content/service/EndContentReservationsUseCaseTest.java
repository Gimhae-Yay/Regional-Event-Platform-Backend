package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestInvalidationReason;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.payment.service.ExpirePendingPaymentForTerminatedHoldUseCase;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;

class EndContentReservationsUseCaseTest {

    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 200L;
    private static final Long ADMIN_ID = 300L;
    private static final int VERSION_NO = 3;
    private static final Instant ENDED_AT = Instant.parse("2026-08-06T00:00:00Z");

    private final ContentService contentService = mock(ContentService.class);
    private final ContentWithdrawalRequestService contentWithdrawalRequestService =
        mock(ContentWithdrawalRequestService.class);
    private final RegionService regionService = mock(RegionService.class);
    private final ContentRevisionInvalidationService contentRevisionInvalidationService =
        mock(ContentRevisionInvalidationService.class);
    private final ContentSessionService contentSessionService = mock(ContentSessionService.class);
    private final ContentLogService contentLogService = mock(ContentLogService.class);
    private final CapacityHoldService capacityHoldService = mock(CapacityHoldService.class);
    private final ExpirePendingPaymentForTerminatedHoldUseCase expirePendingPaymentForTerminatedHoldUseCase =
        mock(ExpirePendingPaymentForTerminatedHoldUseCase.class);
    private final RegionAdminAuthorizationService regionAdminAuthorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase =
        mock(RecordFailedAuditEventUseCase.class);
    private final PublicCatalogCacheInvalidator publicCatalogCacheInvalidator =
        mock(PublicCatalogCacheInvalidator.class);
    private final EndContentReservationsUseCase useCase = new EndContentReservationsUseCase(
        contentService,
        contentWithdrawalRequestService,
        regionService,
        contentRevisionInvalidationService,
        contentSessionService,
        contentLogService,
        capacityHoldService,
        expirePendingPaymentForTerminatedHoldUseCase,
        regionAdminAuthorizationService,
        recordAuditEventUseCase,
        recordFailedAuditEventUseCase,
        publicCatalogCacheInvalidator
    );

    @BeforeEach
    void setUp() {
        when(capacityHoldService.invalidateAllActiveHoldsForContent(CONTENT_ID, "CONTENT_ENDED"))
            .thenReturn(List.of());
        when(contentService.findContentRegionId(CONTENT_ID)).thenReturn(REGION_ID);
        Region lockedRegion = region();
        when(regionService.findRegionForUpdate(REGION_ID)).thenReturn(lockedRegion);
    }

    @Test
    void endByRegionAdmin_성공하면_콘텐츠_캐시_무효화를_등록한다() {
        Content content = publishedContent();
        UserRoleAssignment regionAdmin = activeRegionAdmin();
        ContentSession contentSession = mock(ContentSession.class);
        when(contentService.findEndTargetForUpdate(CONTENT_ID)).thenReturn(content);
        givenAuthorizedRegionAdmin(regionAdmin);
        when(contentSessionService.hasNonTerminalSessionForEnd(CONTENT_ID)).thenReturn(false);
        when(contentSessionService.findCurrentSessionsByContentId(CONTENT_ID)).thenReturn(List.of(contentSession));
        when(contentService.findCurrentDatabaseTime()).thenReturn(ENDED_AT);
        when(contentService.end(content, ENDED_AT)).thenReturn(content);

        useCase.endByRegionAdmin(ADMIN_ID, CONTENT_ID, UUID.randomUUID());

        verify(publicCatalogCacheInvalidator).invalidateContentAfterCommit(REGION_ID, CONTENT_ID, VERSION_NO);
    }

    @Test
    void endBySystem_성공하면_콘텐츠_캐시_무효화를_등록한다() {
        Content content = publishedContent();
        ContentSession completedSession = mock(ContentSession.class);
        when(completedSession.getStatus()).thenReturn(ContentSessionStatus.COMPLETED);
        when(completedSession.getCompletedAt()).thenReturn(ENDED_AT);
        when(contentService.findEndTargetForUpdate(CONTENT_ID)).thenReturn(content);
        when(contentSessionService.findCurrentSessionsByContentId(CONTENT_ID)).thenReturn(List.of(completedSession));
        when(contentSessionService.getEndTerminalStatuses()).thenReturn(List.of(ContentSessionStatus.COMPLETED));
        when(contentService.findCurrentDatabaseTime()).thenReturn(ENDED_AT);
        when(contentService.end(content, ENDED_AT)).thenReturn(content);

        useCase.endBySystem(CONTENT_ID, UUID.randomUUID());

        verify(publicCatalogCacheInvalidator).invalidateContentAfterCommit(REGION_ID, CONTENT_ID, VERSION_NO);
    }

    @Test
    void 수동_종료는_대기_철회_요청을_관리자_actor로_무효화하고_감사한다() {
        Content content = publishedContent();
        UserRoleAssignment regionAdmin = activeRegionAdmin();
        ContentSession contentSession = mock(ContentSession.class);
        ContentWithdrawalRequest request = mock(ContentWithdrawalRequest.class);
        when(request.getContentWithdrawalRequestId()).thenReturn(7001L);
        when(contentService.findEndTargetForUpdate(CONTENT_ID)).thenReturn(content);
        givenAuthorizedRegionAdmin(regionAdmin);
        when(contentSessionService.hasNonTerminalSessionForEnd(CONTENT_ID)).thenReturn(false);
        when(contentSessionService.findCurrentSessionsByContentId(CONTENT_ID)).thenReturn(List.of(contentSession));
        when(contentService.findCurrentDatabaseTime()).thenReturn(ENDED_AT);
        when(contentService.end(content, ENDED_AT)).thenReturn(content);
        AppUser admin = regionAdmin.getAppUser();
        when(contentWithdrawalRequestService.invalidatePendingByUser(
            eq(CONTENT_ID),
            eq(admin),
            eq(ENDED_AT),
            eq(ContentWithdrawalRequestInvalidationReason.CONTENT_ENDED)
        )).thenReturn(Optional.of(request));

        useCase.endByRegionAdmin(ADMIN_ID, CONTENT_ID, UUID.randomUUID());

        ArgumentCaptor<AuditEventCommand> commandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase, times(2)).record(commandCaptor.capture());
        AuditEventCommand requestAudit = commandCaptor.getAllValues().stream()
            .filter(command -> command.targetType() == AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST)
            .findFirst()
            .orElseThrow();
        assertThat(requestAudit.targetId()).isEqualTo(7001L);
        assertThat(requestAudit.reasonCode()).isEqualTo("CONTENT_ENDED");
        assertThat(requestAudit.actor().getAppUser()).isSameAs(admin);
    }

    @Test
    void 자동_종료는_대기_철회_요청을_시스템_actor로_무효화하고_감사한다() {
        Content content = publishedContent();
        ContentSession completedSession = mock(ContentSession.class);
        ContentWithdrawalRequest request = mock(ContentWithdrawalRequest.class);
        when(request.getContentWithdrawalRequestId()).thenReturn(7001L);
        when(completedSession.getStatus()).thenReturn(ContentSessionStatus.COMPLETED);
        when(completedSession.getCompletedAt()).thenReturn(ENDED_AT);
        when(contentService.findEndTargetForUpdate(CONTENT_ID)).thenReturn(content);
        when(contentSessionService.findCurrentSessionsByContentId(CONTENT_ID)).thenReturn(List.of(completedSession));
        when(contentSessionService.getEndTerminalStatuses()).thenReturn(List.of(ContentSessionStatus.COMPLETED));
        when(contentService.findCurrentDatabaseTime()).thenReturn(ENDED_AT);
        when(contentService.end(content, ENDED_AT)).thenReturn(content);
        when(contentWithdrawalRequestService.invalidatePendingBySystem(
            CONTENT_ID,
            ENDED_AT,
            ContentWithdrawalRequestInvalidationReason.CONTENT_ENDED
        )).thenReturn(Optional.of(request));

        useCase.endBySystem(CONTENT_ID, UUID.randomUUID());

        ArgumentCaptor<AuditEventCommand> commandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase, times(2)).record(commandCaptor.capture());
        AuditEventCommand requestAudit = commandCaptor.getAllValues().stream()
            .filter(command -> command.targetType() == AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST)
            .findFirst()
            .orElseThrow();
        assertThat(requestAudit.targetId()).isEqualTo(7001L);
        assertThat(requestAudit.reasonCode()).isEqualTo("CONTENT_ENDED");
        assertThat(requestAudit.actor()).isNull();
    }

    @Test
    void endBySystem_종료_대상이_아니면_캐시_무효화를_등록하지_않는다() {
        Content endedContent = mock(Content.class);
        when(endedContent.getStatus()).thenReturn(ContentStatus.ENDED);
        when(contentService.findEndTargetForUpdate(CONTENT_ID)).thenReturn(endedContent);
        when(contentSessionService.findCurrentSessionsByContentId(CONTENT_ID)).thenReturn(List.of());

        useCase.endBySystem(CONTENT_ID, UUID.randomUUID());

        verifyNoInteractions(publicCatalogCacheInvalidator);
    }

    @Test
    void endByRegionAdmin_이미_종료된_콘텐츠면_캐시_무효화를_등록하지_않는다() {
        Content endedContent = mock(Content.class);
        ContentLog endedLog = mock(ContentLog.class);
        Region region = region();
        UserRoleAssignment regionAdmin = activeRegionAdmin();
        when(endedContent.getStatus()).thenReturn(ContentStatus.ENDED);
        when(endedContent.getRegion()).thenReturn(region);
        when(contentService.findEndTargetForUpdate(CONTENT_ID)).thenReturn(endedContent);
        givenAuthorizedRegionAdmin(regionAdmin);
        when(contentLogService.findLatestEndedForUpdate(CONTENT_ID)).thenReturn(endedLog);
        when(endedLog.getDate()).thenReturn(ENDED_AT);

        EndContentReservationsResult result = useCase.endByRegionAdmin(
            ADMIN_ID,
            CONTENT_ID,
            UUID.randomUUID()
        );

        assertThat(result.status()).isEqualTo(ContentStatus.ENDED);
        assertThat(result.endedAt()).isEqualTo(ENDED_AT);
        verify(contentLogService).findLatestEndedForUpdate(CONTENT_ID);
        verifyNoMoreInteractions(contentLogService);
        verifyNoInteractions(
            contentWithdrawalRequestService,
            contentRevisionInvalidationService,
            contentSessionService,
            capacityHoldService,
            expirePendingPaymentForTerminatedHoldUseCase,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase,
            publicCatalogCacheInvalidator
        );
    }

    private Content publishedContent() {
        Content content = mock(Content.class);
        Region region = region();
        when(content.getStatus()).thenReturn(ContentStatus.PUBLISHED);
        when(content.getRegion()).thenReturn(region);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(content.getVersionNo()).thenReturn(VERSION_NO);
        return content;
    }

    private Region region() {
        Region region = mock(Region.class);
        when(region.getRegionId()).thenReturn(REGION_ID);
        return region;
    }

    private UserRoleAssignment activeRegionAdmin() {
        AppUser appUser = mock(AppUser.class);
        Long assignmentId = 1L;
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        when(appUser.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(assignment.getRoleAssignmentId()).thenReturn(assignmentId);
        when(assignment.getAppUser()).thenReturn(appUser);
        when(appUser.getUserId()).thenReturn(ADMIN_ID);
        return assignment;
    }

    private void givenAuthorizedRegionAdmin(UserRoleAssignment assignment) {
        RegionAdminAuthorizationService.AuthorizedRegionAdmin regionAdmin = mock(
            RegionAdminAuthorizationService.AuthorizedRegionAdmin.class
        );
        when(regionAdminAuthorizationService.requireAuthorizedRegionAdminForUpdate(ADMIN_ID))
            .thenReturn(regionAdmin);
        when(regionAdmin.authorize(REGION_ID)).thenReturn(assignment);
    }
}
