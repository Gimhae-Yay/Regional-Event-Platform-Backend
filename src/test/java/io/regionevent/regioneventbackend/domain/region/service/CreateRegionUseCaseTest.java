package io.regionevent.regioneventbackend.domain.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.CreateRegionUseCase.CreateRegionCommand;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ExtendWith(MockitoExtension.class)
class CreateRegionUseCaseTest {

    private static final Long ACTOR_USER_ID = 101L;
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-09T00:00:00Z");

    @Mock
    private PlatformAdminAuthorizationService platformAdminAuthorizationService;

    @Mock
    private RegionService regionService;

    @Mock
    private RecordAuditEventUseCase recordAuditEventUseCase;

    private CreateRegionUseCase createRegionUseCase;

    @BeforeEach
    void setUp() {
        createRegionUseCase = new CreateRegionUseCase(
            platformAdminAuthorizationService,
            regionService,
            recordAuditEventUseCase,
            Clock.fixed(OCCURRED_AT, ZoneOffset.UTC)
        );
    }

    @Test
    void create_활성플랫폼관리자_정규화된비공개지역과성공감사를생성한다() {
        PlatformAdminAssignment actor = authorizedPlatformAdmin();
        Region region = createdRegion();
        UUID requestId = UUID.randomUUID();
        when(platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(ACTOR_USER_ID))
            .thenReturn(actor);
        when(regionService.createPrivateRegion("JEONJU", "전주시")).thenReturn(region);

        CreateRegionResult result = createRegionUseCase.create(
            ACTOR_USER_ID,
            new CreateRegionCommand(
                "  jeonju  ",
                "  전주시  ",
                "  PILOT_REGION_ADDITION  ",
                "  OPS-2026-0805-REGION-03  "
            ),
            requestId
        );

        assertThat(result.regionId()).isEqualTo(11L);
        assertThat(result.regionCode()).isEqualTo("JEONJU");
        assertThat(result.name()).isEqualTo("전주시");
        assertThat(result.isPublic()).isFalse();
        verify(regionService).createPrivateRegion("JEONJU", "전주시");

        ArgumentCaptor<AuditEventCommand> auditEventCaptor = ArgumentCaptor.forClass(
            AuditEventCommand.class
        );
        verify(recordAuditEventUseCase).record(auditEventCaptor.capture());
        AuditEventCommand auditEvent = auditEventCaptor.getValue();
        assertThat(auditEvent.requestId()).isEqualTo(requestId);
        assertThat(auditEvent.region()).isSameAs(region);
        assertThat(auditEvent.targetType()).isEqualTo(AuditEventTargetType.REGION);
        assertThat(auditEvent.targetId()).isEqualTo(11L);
        assertThat(auditEvent.previousState()).isNull();
        assertThat(auditEvent.nextState()).isEqualTo("CREATED");
        assertThat(auditEvent.result()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(auditEvent.reasonCode()).isEqualTo("PILOT_REGION_ADDITION");
        assertThat(auditEvent.evidenceReference()).isEqualTo("OPS-2026-0805-REGION-03");
        assertThat(auditEvent.actor().getRoleName()).isEqualTo("PLATFORM_ADMIN");
        assertThat(auditEvent.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void create_잘못된입력_권한과지역생성을시도하지않는다() {
        assertThatThrownBy(() -> createRegionUseCase.create(
            ACTOR_USER_ID,
            new CreateRegionCommand(
                "1-jeonju",
                "전주시",
                "PILOT_REGION_ADDITION",
                "OPS-2026-0805-REGION-03"
            ),
            UUID.randomUUID()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
        );

        verify(platformAdminAuthorizationService, never()).requireAuthorizedPlatformAdmin(any());
        verify(regionService, never()).createPrivateRegion(any(), any());
        verify(recordAuditEventUseCase, never()).record(any());
    }

    @Test
    void create_고권한배정이없음_지역과감사를생성하지않는다() {
        when(platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(ACTOR_USER_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> createRegionUseCase.create(
            ACTOR_USER_ID,
            command(),
            UUID.randomUUID()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
        );

        verify(regionService, never()).createPrivateRegion(any(), any());
        verify(recordAuditEventUseCase, never()).record(any());
    }

    @Test
    void create_정규화코드충돌_감사를생성하지않는다() {
        PlatformAdminAssignment actor = org.mockito.Mockito.mock(PlatformAdminAssignment.class);
        when(platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(ACTOR_USER_ID))
            .thenReturn(actor);
        when(regionService.createPrivateRegion(eq("JEONJU"), eq("전주시")))
            .thenThrow(new BusinessException(ErrorCode.REGION_CODE_ALREADY_EXISTS));

        assertThatThrownBy(() -> createRegionUseCase.create(
            ACTOR_USER_ID,
            command(),
            UUID.randomUUID()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REGION_CODE_ALREADY_EXISTS)
        );

        verify(recordAuditEventUseCase, never()).record(any());
    }

    private CreateRegionCommand command() {
        return new CreateRegionCommand(
            "jeonju",
            "전주시",
            "PILOT_REGION_ADDITION",
            "OPS-2026-0805-REGION-03"
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

    private Region createdRegion() {
        Region region = org.mockito.Mockito.mock(Region.class);
        when(region.getRegionId()).thenReturn(11L);
        when(region.getRegionCode()).thenReturn("JEONJU");
        when(region.getName()).thenReturn("전주시");
        when(region.isPublic()).thenReturn(false);
        when(region.getCreatedAt()).thenReturn(OCCURRED_AT);
        when(region.getUpdatedAt()).thenReturn(OCCURRED_AT);
        return region;
    }
}
