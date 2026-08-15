package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponPolicyService;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.repository.MissionUpdateSnapshot;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class SubmitOperatorMissionUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long MISSION_ID = 701L;
    private static final Long REGION_ID = 11L;
    private static final Long COUPON_POLICY_ID = 501L;
    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000632");

    private OperatorAuthorizationService operatorAuthorizationService;
    private CouponPolicyService couponPolicyService;
    private MissionService missionService;
    private RecordAuditEventUseCase recordAuditEventUseCase;
    private RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private SubmitOperatorMissionUseCase useCase;

    @BeforeEach
    void setUp() {
        operatorAuthorizationService = mock(OperatorAuthorizationService.class);
        couponPolicyService = mock(CouponPolicyService.class);
        missionService = mock(MissionService.class);
        recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
        recordFailedAuditEventUseCase = mock(RecordFailedAuditEventUseCase.class);
        useCase = new SubmitOperatorMissionUseCase(
            operatorAuthorizationService,
            couponPolicyService,
            missionService,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void submit_withDraftMission_recordsPendingReviewSuccessAuditAfterLockingPolicyAndMission() {
        Mission lockedMission = mission(COUPON_POLICY_ID, MissionStatus.DRAFT, REGION_ID);
        Mission submittedMission = mission(COUPON_POLICY_ID, MissionStatus.PENDING_REVIEW, REGION_ID);
        AuthorizedOperator operator = operator();
        CouponPolicy rewardCouponPolicy = rewardCouponPolicy(REGION_ID, CouponIssuanceType.MISSION_REWARD,
            CouponPolicyStatus.DRAFT);
        org.mockito.Mockito.doReturn(snapshot(REGION_ID, MissionStatus.DRAFT, COUPON_POLICY_ID))
            .when(missionService).findUpdateSnapshot(MISSION_ID);
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(USER_ID)).thenReturn(operator);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID)).thenReturn(rewardCouponPolicy);
        when(missionService.findByMissionIdForUpdate(MISSION_ID)).thenReturn(lockedMission);
        when(missionService.submitForReview(lockedMission)).thenReturn(submittedMission);

        SubmitOperatorMissionResult result = useCase.submit(USER_ID, MISSION_ID, REQUEST_ID);

        assertThat(result).isEqualTo(new SubmitOperatorMissionResult(MISSION_ID, MissionStatus.PENDING_REVIEW));
        InOrder lockOrder = inOrder(couponPolicyService, missionService);
        lockOrder.verify(couponPolicyService).findForUpdate(COUPON_POLICY_ID);
        lockOrder.verify(missionService).findByMissionIdForUpdate(MISSION_ID);
        ArgumentCaptor<AuditEventCommand> auditCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue()).satisfies(audit -> {
            assertThat(audit.requestId()).isEqualTo(REQUEST_ID);
            assertThat(audit.targetType()).isEqualTo(AuditEventTargetType.MISSION);
            assertThat(audit.targetId()).isEqualTo(MISSION_ID);
            assertThat(audit.previousState()).isEqualTo(MissionStatus.DRAFT.name());
            assertThat(audit.nextState()).isEqualTo(MissionStatus.PENDING_REVIEW.name());
            assertThat(audit.result()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(audit.reasonCode()).isEqualTo("MISSION_SUBMITTED");
            assertThat(audit.reason()).isNull();
            assertThat(audit.occurredAt()).isEqualTo(NOW);
        });
        verifyNoInteractions(recordFailedAuditEventUseCase);
    }

    @Test
    void submit_whenLockedMissionRewardPolicyChanged_throwsConflictWithoutLockingChangedPolicy() {
        Mission lockedMission = mission(502L, MissionStatus.DRAFT, REGION_ID);
        AuthorizedOperator operator = operator();
        CouponPolicy rewardCouponPolicy = rewardCouponPolicy(
            REGION_ID,
            CouponIssuanceType.MISSION_REWARD,
            CouponPolicyStatus.PUBLISHED
        );
        org.mockito.Mockito.doReturn(snapshot(REGION_ID, MissionStatus.DRAFT, COUPON_POLICY_ID))
            .when(missionService).findUpdateSnapshot(MISSION_ID);
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(USER_ID)).thenReturn(operator);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID)).thenReturn(rewardCouponPolicy);
        when(missionService.findByMissionIdForUpdate(MISSION_ID)).thenReturn(lockedMission);

        assertThatThrownBy(() -> useCase.submit(USER_ID, MISSION_ID, REQUEST_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.MISSION_STATE_CONFLICT);

        verify(couponPolicyService).findForUpdate(COUPON_POLICY_ID);
        verifyNoInteractions(recordAuditEventUseCase);
        verify(missionService, org.mockito.Mockito.never()).submitForReview(lockedMission);
        assertFailureAudit(ErrorCode.MISSION_STATE_CONFLICT, MissionStatus.DRAFT);
    }

    @Test
    void submit_withNonDraftMission_throwsStateConflictWithoutChangingMissionOrRecordingAudit() {
        Mission lockedMission = mission(COUPON_POLICY_ID, MissionStatus.PENDING_REVIEW, REGION_ID);
        AuthorizedOperator operator = operator();
        CouponPolicy rewardCouponPolicy = rewardCouponPolicy(
            REGION_ID,
            CouponIssuanceType.MISSION_REWARD,
            CouponPolicyStatus.PUBLISHED
        );
        org.mockito.Mockito.doReturn(snapshot(REGION_ID, MissionStatus.PENDING_REVIEW, COUPON_POLICY_ID))
            .when(missionService).findUpdateSnapshot(MISSION_ID);
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(USER_ID)).thenReturn(operator);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID)).thenReturn(rewardCouponPolicy);
        when(missionService.findByMissionIdForUpdate(MISSION_ID)).thenReturn(lockedMission);

        assertThatThrownBy(() -> useCase.submit(USER_ID, MISSION_ID, REQUEST_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.MISSION_STATE_CONFLICT);

        verify(missionService, org.mockito.Mockito.never()).submitForReview(lockedMission);
        verifyNoInteractions(recordAuditEventUseCase);
        assertFailureAudit(ErrorCode.MISSION_STATE_CONFLICT, MissionStatus.PENDING_REVIEW);
    }

    @Test
    void submit_whenRewardCouponPolicyDoesNotExist_propagatesNotFoundWithoutLockingMission() {
        AuthorizedOperator operator = operator();
        org.mockito.Mockito.doReturn(snapshot(REGION_ID, MissionStatus.DRAFT, COUPON_POLICY_ID))
            .when(missionService).findUpdateSnapshot(MISSION_ID);
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(USER_ID)).thenReturn(operator);
        when(couponPolicyService.findForUpdate(COUPON_POLICY_ID))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> useCase.submit(USER_ID, MISSION_ID, REQUEST_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.NOT_FOUND);

        verifyNoInteractions(recordAuditEventUseCase);
        verify(missionService, org.mockito.Mockito.never()).findByMissionIdForUpdate(MISSION_ID);
        assertFailureAudit(ErrorCode.NOT_FOUND, MissionStatus.DRAFT);
    }

    @Test
    void submit_withoutAuthorizedOperator_doesNotRecordFailureAudit() {
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(USER_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> useCase.submit(USER_ID, MISSION_ID, REQUEST_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(couponPolicyService, recordAuditEventUseCase, recordFailedAuditEventUseCase);
        verify(missionService, org.mockito.Mockito.never()).findUpdateSnapshot(MISSION_ID);
    }

    @Test
    void submit_whenMissionDoesNotExist_doesNotRecordFailureAudit() {
        org.mockito.Mockito.doReturn(operator())
            .when(operatorAuthorizationService).requireAuthorizedOperatorForUpdate(USER_ID);
        when(missionService.findUpdateSnapshot(MISSION_ID)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> useCase.submit(USER_ID, MISSION_ID, REQUEST_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.NOT_FOUND);

        verifyNoInteractions(couponPolicyService, recordAuditEventUseCase, recordFailedAuditEventUseCase);
    }

    @Test
    void submit_whenProcessingExceptionOccurs_recordsInternalServerErrorFailureAudit() {
        Mission lockedMission = mission(COUPON_POLICY_ID, MissionStatus.DRAFT, REGION_ID);
        org.mockito.Mockito.doReturn(operator())
            .when(operatorAuthorizationService).requireAuthorizedOperatorForUpdate(USER_ID);
        org.mockito.Mockito.doReturn(snapshot(REGION_ID, MissionStatus.DRAFT, COUPON_POLICY_ID))
            .when(missionService).findUpdateSnapshot(MISSION_ID);
        org.mockito.Mockito.doReturn(rewardCouponPolicy(
                REGION_ID,
                CouponIssuanceType.MISSION_REWARD,
                CouponPolicyStatus.DRAFT
            ))
            .when(couponPolicyService).findForUpdate(COUPON_POLICY_ID);
        when(missionService.findByMissionIdForUpdate(MISSION_ID)).thenReturn(lockedMission);
        when(missionService.submitForReview(lockedMission))
            .thenThrow(new IllegalStateException("storage failure"));

        assertThatThrownBy(() -> useCase.submit(USER_ID, MISSION_ID, REQUEST_ID))
            .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(recordAuditEventUseCase);
        assertFailureAudit(ErrorCode.INTERNAL_SERVER_ERROR, MissionStatus.DRAFT);
    }

    private AuthorizedOperator operator() {
        AppUser user = mock(AppUser.class);
        Region region = region(REGION_ID);
        UserRoleAssignment roleAssignment = mock(UserRoleAssignment.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(user.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(roleAssignment.getRoleAssignmentId()).thenReturn(900L);
        when(roleAssignment.getAppUser()).thenReturn(user);
        when(roleAssignment.getRole()).thenReturn(UserRole.OPERATOR);
        return new AuthorizedOperator(user, region, roleAssignment);
    }

    private MissionUpdateSnapshot snapshot(
        Long regionId,
        MissionStatus status,
        Long couponPolicyId
    ) {
        MissionUpdateSnapshot snapshot = mock(MissionUpdateSnapshot.class);
        Region region = region(regionId);
        when(snapshot.getRegion()).thenReturn(region);
        when(snapshot.getStatus()).thenReturn(status);
        when(snapshot.getRewardCouponPolicyId()).thenReturn(couponPolicyId);
        return snapshot;
    }

    private void assertFailureAudit(
        ErrorCode errorCode,
        MissionStatus previousState
    ) {
        ArgumentCaptor<AuditEventCommand> auditCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordFailedAuditEventUseCase).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue()).satisfies(audit -> {
            assertThat(audit.requestId()).isEqualTo(REQUEST_ID);
            assertThat(audit.region().getRegionId()).isEqualTo(REGION_ID);
            assertThat(audit.targetType()).isEqualTo(AuditEventTargetType.MISSION);
            assertThat(audit.targetId()).isEqualTo(MISSION_ID);
            assertThat(audit.previousState()).isEqualTo(previousState.name());
            assertThat(audit.nextState()).isNull();
            assertThat(audit.result()).isEqualTo(AuditEventResult.FAILURE);
            assertThat(audit.reasonCode()).isEqualTo(errorCode.code());
            assertThat(audit.actor().getRole()).isEqualTo(UserRole.OPERATOR);
            assertThat(audit.occurredAt()).isEqualTo(NOW);
        });
    }

    private Mission mission(
        Long couponPolicyId,
        MissionStatus status,
        Long regionId
    ) {
        Mission mission = mock(Mission.class);
        CouponPolicy couponPolicy = mock(CouponPolicy.class);
        Region region = region(regionId);
        when(couponPolicy.getCouponPolicyId()).thenReturn(couponPolicyId);
        when(mission.getMissionId()).thenReturn(MISSION_ID);
        when(mission.getRewardCouponPolicy()).thenReturn(couponPolicy);
        when(mission.getRegion()).thenReturn(region);
        when(mission.getStatus()).thenReturn(status);
        return mission;
    }

    private CouponPolicy rewardCouponPolicy(
        Long regionId,
        CouponIssuanceType issuanceType,
        CouponPolicyStatus status
    ) {
        CouponPolicy couponPolicy = mock(CouponPolicy.class);
        Region region = region(regionId);
        when(couponPolicy.getRegion()).thenReturn(region);
        when(couponPolicy.getIssuanceType()).thenReturn(issuanceType);
        when(couponPolicy.getStatus()).thenReturn(status);
        return couponPolicy;
    }

    private Region region(Long regionId) {
        Region region = mock(Region.class);
        when(region.getRegionId()).thenReturn(regionId);
        return region;
    }
}
