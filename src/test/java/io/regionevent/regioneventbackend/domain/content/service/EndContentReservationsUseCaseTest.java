package io.regionevent.regioneventbackend.domain.content.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentId;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;

class EndContentReservationsUseCaseTest {

    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 200L;
    private static final Long ADMIN_ID = 300L;
    private static final int VERSION_NO = 3;
    private static final Instant ENDED_AT = Instant.parse("2026-08-06T00:00:00Z");

    private final ContentService contentService = mock(ContentService.class);
    private final ContentSessionService contentSessionService = mock(ContentSessionService.class);
    private final ContentLogService contentLogService = mock(ContentLogService.class);
    private final CapacityHoldService capacityHoldService = mock(CapacityHoldService.class);
    private final RegionAdminAuthorizationService regionAdminAuthorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase =
        mock(RecordFailedAuditEventUseCase.class);
    private final PublicCatalogCacheInvalidator publicCatalogCacheInvalidator =
        mock(PublicCatalogCacheInvalidator.class);
    private final EndContentReservationsUseCase useCase = new EndContentReservationsUseCase(
        contentService,
        contentSessionService,
        contentLogService,
        capacityHoldService,
        regionAdminAuthorizationService,
        recordAuditEventUseCase,
        recordFailedAuditEventUseCase,
        publicCatalogCacheInvalidator
    );

    @Test
    void endByRegionAdmin_성공하면_콘텐츠_캐시_무효화를_등록한다() {
        Content content = publishedContent();
        UserRoleAssignment regionAdmin = activeRegionAdmin();
        ContentSession contentSession = mock(ContentSession.class);
        when(contentService.findEndTargetForUpdate(CONTENT_ID)).thenReturn(content);
        when(regionAdminAuthorizationService.authorize(ADMIN_ID, REGION_ID)).thenReturn(regionAdmin);
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
        when(regionAdminAuthorizationService.authorize(ADMIN_ID, REGION_ID)).thenReturn(regionAdmin);
        when(contentLogService.findLatestEnded(CONTENT_ID)).thenReturn(endedLog);
        when(endedLog.getDate()).thenReturn(ENDED_AT);

        useCase.endByRegionAdmin(ADMIN_ID, CONTENT_ID, UUID.randomUUID());

        verifyNoInteractions(publicCatalogCacheInvalidator);
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
        UserRoleAssignmentId assignmentId = mock(UserRoleAssignmentId.class);
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        when(appUser.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(assignmentId.getUserId()).thenReturn(ADMIN_ID);
        when(assignment.getId()).thenReturn(assignmentId);
        when(assignment.getAppUser()).thenReturn(appUser);
        return assignment;
    }
}
