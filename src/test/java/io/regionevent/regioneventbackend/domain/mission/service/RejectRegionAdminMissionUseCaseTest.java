package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService.AuthorizedRegionAdmin;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class RejectRegionAdminMissionUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long REGION_ID = 11L;
    private static final Long MISSION_ID = 701L;
    private static final String REASON_CODE = "MISSION_REWARD_POLICY_INVALID";
    private static final Instant REJECTED_AT = Instant.parse("2026-08-10T04:30:00.123456Z");
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final RegionAdminAuthorizationService authorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final MissionService missionService = mock(MissionService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase =
        mock(RecordFailedAuditEventUseCase.class);
    private final RejectRegionAdminMissionUseCase useCase = new RejectRegionAdminMissionUseCase(
        authorizationService,
        missionService,
        recordAuditEventUseCase,
        recordFailedAuditEventUseCase,
        Clock.fixed(REJECTED_AT, ZoneOffset.UTC)
    );

    private Region region;
    private Mission initialMission;
    private Mission lockedMission;
    private Mission rejectedMission;

    @BeforeEach
    void setUp() {
        region = mock(Region.class);
        when(region.getRegionId()).thenReturn(REGION_ID);
        AppUser user = mock(AppUser.class);
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(user.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(assignment.getRoleAssignmentId()).thenReturn(900L);
        when(assignment.getAppUser()).thenReturn(user);
        when(assignment.getRole()).thenReturn(UserRole.REGION_ADMIN);
        AuthorizedRegionAdmin regionAdmin = new AuthorizedRegionAdmin(user, region, assignment);
        when(authorizationService.requireAuthorizedRegionAdminForUpdate(USER_ID))
            .thenReturn(regionAdmin);

        initialMission = mission();
        lockedMission = mission();
        rejectedMission = mission();
        when(rejectedMission.getStatus()).thenReturn(MissionStatus.DRAFT);
        when(missionService.findMission(MISSION_ID)).thenReturn(initialMission);
        when(missionService.findForUpdate(MISSION_ID)).thenReturn(lockedMission);
        when(missionService.reject(lockedMission)).thenReturn(rejectedMission);
    }

    @Test
    void reject_locksMissionRevalidatesRegionAndRecordsSuccessAudit() {
        RejectRegionAdminMissionResult result = useCase.reject(
            USER_ID,
            MISSION_ID,
            REASON_CODE,
            REQUEST_ID
        );

        assertThat(result.missionId()).isEqualTo(MISSION_ID);
        assertThat(result.status()).isEqualTo(MissionStatus.DRAFT);
        assertThat(result.rejectedAt()).isEqualTo(REJECTED_AT);
        InOrder order = inOrder(missionService, recordAuditEventUseCase);
        order.verify(missionService).findMission(MISSION_ID);
        order.verify(missionService).findForUpdate(MISSION_ID);
        order.verify(missionService).reject(lockedMission);
        order.verify(recordAuditEventUseCase).record(org.mockito.ArgumentMatchers.any());
        org.mockito.ArgumentCaptor<AuditEventCommand> captor =
            org.mockito.ArgumentCaptor.forClass(AuditEventCommand.class);
        org.mockito.Mockito.verify(recordAuditEventUseCase).record(captor.capture());
        assertThat(captor.getValue().result()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(captor.getValue().reasonCode()).isEqualTo(REASON_CODE);
        verifyNoInteractions(recordFailedAuditEventUseCase);
    }

    @Test
    void reject_withDisallowedReasonCode_returnsInvalidInputBeforeStateChange() {
        assertThatThrownBy(() -> useCase.reject(USER_ID, MISSION_ID, "PERSONAL_OPINION", REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );

        verifyNoInteractions(
            authorizationService,
            missionService,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase
        );
    }

    @Test
    void reject_withoutRegionAdminRole_doesNotRecordFailureAudit() {
        when(authorizationService.requireAuthorizedRegionAdminForUpdate(USER_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> useCase.reject(USER_ID, MISSION_ID, REASON_CODE, REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verifyNoInteractions(missionService, recordAuditEventUseCase, recordFailedAuditEventUseCase);
    }

    @Test
    void reject_whenMissionDoesNotExist_doesNotRecordFailureAudit() {
        when(missionService.findMission(MISSION_ID)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> useCase.reject(USER_ID, MISSION_ID, REASON_CODE, REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );

        verifyNoInteractions(recordAuditEventUseCase, recordFailedAuditEventUseCase);
    }

    @Test
    void reject_withOtherRegionMission_recordsForbiddenFailureAudit() {
        Region otherRegion = mock(Region.class);
        when(otherRegion.getRegionId()).thenReturn(12L);
        when(initialMission.getRegion()).thenReturn(otherRegion);

        assertThatThrownBy(() -> useCase.reject(USER_ID, MISSION_ID, REASON_CODE, REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        assertFailureAudit(ErrorCode.FORBIDDEN, otherRegion, MissionStatus.PENDING_REVIEW);
    }

    @Test
    void reject_whenProcessingExceptionOccurs_recordsInternalServerErrorFailureAudit() {
        when(missionService.reject(lockedMission)).thenThrow(new IllegalStateException("storage failure"));

        assertThatThrownBy(() -> useCase.reject(USER_ID, MISSION_ID, REASON_CODE, REQUEST_ID))
            .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(recordAuditEventUseCase);
        assertFailureAudit(ErrorCode.INTERNAL_SERVER_ERROR, region, MissionStatus.PENDING_REVIEW);
    }

    private void assertFailureAudit(
        ErrorCode errorCode,
        Region auditRegion,
        MissionStatus previousState
    ) {
        org.mockito.ArgumentCaptor<AuditEventCommand> captor =
            org.mockito.ArgumentCaptor.forClass(AuditEventCommand.class);
        org.mockito.Mockito.verify(recordFailedAuditEventUseCase).record(captor.capture());
        assertThat(captor.getValue()).satisfies(audit -> {
            assertThat(audit.requestId()).isEqualTo(REQUEST_ID);
            assertThat(audit.region()).isSameAs(auditRegion);
            assertThat(audit.targetType()).isEqualTo(AuditEventTargetType.MISSION);
            assertThat(audit.targetId()).isEqualTo(MISSION_ID);
            assertThat(audit.previousState()).isEqualTo(previousState.name());
            assertThat(audit.nextState()).isNull();
            assertThat(audit.result()).isEqualTo(AuditEventResult.FAILURE);
            assertThat(audit.reasonCode()).isEqualTo(errorCode.code());
            assertThat(audit.actor().getRole()).isEqualTo(UserRole.REGION_ADMIN);
            assertThat(audit.occurredAt()).isEqualTo(REJECTED_AT);
        });
    }

    private Mission mission() {
        Mission mission = mock(Mission.class);
        when(mission.getMissionId()).thenReturn(MISSION_ID);
        when(mission.getRegion()).thenReturn(region);
        when(mission.getStatus()).thenReturn(MissionStatus.PENDING_REVIEW);
        return mission;
    }
}
