package io.regionevent.regioneventbackend.domain.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.UpdateRegionStatusUseCase.UpdateRegionStatusCommand;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ExtendWith(MockitoExtension.class)
class UpdateRegionStatusUseCaseTest {

    private static final Long ACTOR_USER_ID = 101L;
    private static final Long REGION_ID = 11L;
    private static final Instant UPDATED_AT = Instant.parse("2026-08-09T01:00:00Z");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-09T02:00:00Z");

    @Mock
    private PlatformAdminAuthorizationService platformAdminAuthorizationService;

    @Mock
    private RegionService regionService;

    @Mock
    private ContentService contentService;

    @Mock
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Mock
    private RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;

    private UpdateRegionStatusUseCase updateRegionStatusUseCase;

    @BeforeEach
    void setUp() {
        updateRegionStatusUseCase = new UpdateRegionStatusUseCase(
            platformAdminAuthorizationService,
            regionService,
            contentService,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase,
            Clock.fixed(OCCURRED_AT, ZoneOffset.UTC)
        );
    }

    @Test
    void update_비공개지역을공개하면_상태전이와성공감사를요청한다() {
        PlatformAdminAssignment actor = authorizedPlatformAdmin();
        Region privateRegion = visibilityRegion(false);
        Region publicRegion = resultRegion(true, OCCURRED_AT);
        UUID requestId = UUID.randomUUID();
        when(platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(ACTOR_USER_ID))
            .thenReturn(actor);
        when(regionService.findRegionForUpdate(REGION_ID)).thenReturn(privateRegion);
        when(regionService.changeVisibility(privateRegion, true)).thenReturn(publicRegion);

        UpdateRegionStatusResult result = updateRegionStatusUseCase.update(
            ACTOR_USER_ID,
            REGION_ID,
            command(true, "REGION_LAUNCH"),
            requestId
        );

        assertThat(result.isPublic()).isTrue();
        assertThat(result.updatedAt()).isEqualTo(OCCURRED_AT);
        verify(regionService).changeVisibility(privateRegion, true);
        verify(contentService, never()).hasUndeletedContentInRegion(any());

        ArgumentCaptor<AuditEventCommand> auditEventCaptor = ArgumentCaptor.forClass(
            AuditEventCommand.class
        );
        verify(recordAuditEventUseCase).record(auditEventCaptor.capture());
        AuditEventCommand auditEvent = auditEventCaptor.getValue();
        assertThat(auditEvent.requestId()).isEqualTo(requestId);
        assertThat(auditEvent.targetType()).isEqualTo(AuditEventTargetType.REGION);
        assertThat(auditEvent.targetId()).isEqualTo(REGION_ID);
        assertThat(auditEvent.previousState()).isEqualTo("FALSE");
        assertThat(auditEvent.nextState()).isEqualTo("TRUE");
        assertThat(auditEvent.result()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(auditEvent.reasonCode()).isEqualTo("REGION_LAUNCH");
        assertThat(auditEvent.evidenceReference()).isEqualTo("OPS-2026-0805-REGION-03");
        assertThat(auditEvent.actor().getRoleName()).isEqualTo("PLATFORM_ADMIN");
        assertThat(auditEvent.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void update_동일상태요청은지역과감사를변경하지않는다() {
        PlatformAdminAssignment actor = org.mockito.Mockito.mock(PlatformAdminAssignment.class);
        Region privateRegion = resultRegion(false, UPDATED_AT);
        when(platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(ACTOR_USER_ID))
            .thenReturn(actor);
        when(regionService.findRegionForUpdate(REGION_ID)).thenReturn(privateRegion);

        UpdateRegionStatusResult result = updateRegionStatusUseCase.update(
            ACTOR_USER_ID,
            REGION_ID,
            command(false, "REGION_PREPARATION"),
            UUID.randomUUID()
        );

        assertThat(result.isPublic()).isFalse();
        assertThat(result.updatedAt()).isEqualTo(UPDATED_AT);
        verify(regionService, never()).changeVisibility(any(), anyBoolean());
        verify(contentService, never()).hasUndeletedContentInRegion(any());
        verify(recordAuditEventUseCase, never()).record(any());
        verify(recordFailedAuditEventUseCase, never()).record(any());
    }

    @Test
    void update_콘텐츠가있는공개지역을비공개로바꾸면_실패감사를등록하고충돌을반환한다() {
        PlatformAdminAssignment actor = authorizedPlatformAdmin();
        Region publicRegion = lockedRegion(true);
        UUID requestId = UUID.randomUUID();
        when(platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(ACTOR_USER_ID))
            .thenReturn(actor);
        when(regionService.findRegionForUpdate(REGION_ID)).thenReturn(publicRegion);
        when(contentService.hasUndeletedContentInRegion(REGION_ID)).thenReturn(true);

        assertThatThrownBy(() -> updateRegionStatusUseCase.update(
            ACTOR_USER_ID,
            REGION_ID,
            command(false, "REGION_PREPARATION"),
            requestId
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REGION_AVAILABILITY_CONFLICT)
        );

        verify(regionService, never()).changeVisibility(any(), anyBoolean());
        verify(recordAuditEventUseCase, never()).record(any());
        ArgumentCaptor<AuditEventCommand> auditEventCaptor = ArgumentCaptor.forClass(
            AuditEventCommand.class
        );
        verify(recordFailedAuditEventUseCase).record(auditEventCaptor.capture());
        AuditEventCommand auditEvent = auditEventCaptor.getValue();
        assertThat(auditEvent.requestId()).isEqualTo(requestId);
        assertThat(auditEvent.targetType()).isEqualTo(AuditEventTargetType.REGION);
        assertThat(auditEvent.targetId()).isEqualTo(REGION_ID);
        assertThat(auditEvent.previousState()).isEqualTo("TRUE");
        assertThat(auditEvent.nextState()).isNull();
        assertThat(auditEvent.result()).isEqualTo(AuditEventResult.FAILURE);
        assertThat(auditEvent.reasonCode()).isEqualTo("REGION_AVAILABILITY_CONFLICT");
        assertThat(auditEvent.evidenceReference()).isEqualTo("OPS-2026-0805-REGION-03");
        assertThat(auditEvent.actor().getRoleName()).isEqualTo("PLATFORM_ADMIN");
    }

    @Test
    void update_목표공개여부에맞지않는사유는인가와상태조회를시도하지않는다() {
        assertThatThrownBy(() -> updateRegionStatusUseCase.update(
            ACTOR_USER_ID,
            REGION_ID,
            command(true, "REGION_PREPARATION"),
            UUID.randomUUID()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
        );

        verify(platformAdminAuthorizationService, never()).requireAuthorizedPlatformAdmin(any());
        verify(regionService, never()).findRegionForUpdate(any());
        verify(recordAuditEventUseCase, never()).record(any());
        verify(recordFailedAuditEventUseCase, never()).record(any());
    }

    @Test
    void update_고권한배정이없으면_지역과감사를변경하지않는다() {
        when(platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(ACTOR_USER_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> updateRegionStatusUseCase.update(
            ACTOR_USER_ID,
            REGION_ID,
            command(true, "REGION_LAUNCH"),
            UUID.randomUUID()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
        );

        verify(regionService, never()).findRegionForUpdate(any());
        verify(recordAuditEventUseCase, never()).record(any());
        verify(recordFailedAuditEventUseCase, never()).record(any());
    }

    private UpdateRegionStatusCommand command(boolean isPublic, String reasonCode) {
        return new UpdateRegionStatusCommand(
            isPublic,
            reasonCode,
            "  OPS-2026-0805-REGION-03  "
        );
    }

    private PlatformAdminAssignment authorizedPlatformAdmin() {
        AppUser appUser = org.mockito.Mockito.mock(AppUser.class);
        PlatformAdminAssignment actor = org.mockito.Mockito.mock(PlatformAdminAssignment.class);
        when(appUser.getUserId()).thenReturn(ACTOR_USER_ID);
        when(appUser.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(appUser.getAccountKind()).thenReturn(AppUserAccountKind.PRIVILEGED);
        when(actor.getPlatformAdminAssignmentId()).thenReturn(1L);
        when(actor.getAppUser()).thenReturn(appUser);
        when(actor.isActive()).thenReturn(true);
        when(actor.getGrade()).thenReturn(PlatformAdminGrade.PLATFORM_ADMIN);
        return actor;
    }

    private Region lockedRegion(boolean isPublic) {
        Region region = org.mockito.Mockito.mock(Region.class);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(region.isPublic()).thenReturn(isPublic);
        return region;
    }

    private Region visibilityRegion(boolean isPublic) {
        Region region = org.mockito.Mockito.mock(Region.class);
        when(region.isPublic()).thenReturn(isPublic);
        return region;
    }

    private Region resultRegion(boolean isPublic, Instant updatedAt) {
        Region region = lockedRegion(isPublic);
        when(region.getRegionCode()).thenReturn("JEONJU");
        when(region.getName()).thenReturn("전주시");
        when(region.getUpdatedAt()).thenReturn(updatedAt);
        return region;
    }
}
