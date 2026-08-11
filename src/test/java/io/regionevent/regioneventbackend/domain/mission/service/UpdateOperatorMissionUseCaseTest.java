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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponPolicyService;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class UpdateOperatorMissionUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000629");

    private OperatorAuthorizationService operatorAuthorizationService;
    private CouponPolicyService couponPolicyService;
    private MissionService missionService;
    private ContentService contentService;
    private MissionTargetContentService missionTargetContentService;
    private RecordAuditEventUseCase recordAuditEventUseCase;
    private RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private UpdateOperatorMissionUseCase useCase;

    @BeforeEach
    void setUp() {
        operatorAuthorizationService = mock(OperatorAuthorizationService.class);
        couponPolicyService = mock(CouponPolicyService.class);
        missionService = mock(MissionService.class);
        contentService = mock(ContentService.class);
        missionTargetContentService = mock(MissionTargetContentService.class);
        recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
        recordFailedAuditEventUseCase = mock(RecordFailedAuditEventUseCase.class);
        useCase = new UpdateOperatorMissionUseCase(
            operatorAuthorizationService,
            couponPolicyService,
            missionService,
            contentService,
            missionTargetContentService,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void update_withContentSet_replacesAllValuesInDocumentedLockOrderAndRecordsAudit() {
        AuthorizedOperator operator = operator(100L, 11L, 900L);
        CouponPolicy currentPolicy = rewardPolicy(502L, 11L, CouponPolicyStatus.DRAFT);
        CouponPolicy requestedPolicy = rewardPolicy(501L, 11L, CouponPolicyStatus.PUBLISHED);
        Mission initialMission = mission(701L, 11L, MissionStatus.DRAFT, currentPolicy);
        Mission lockedMission = mission(701L, 11L, MissionStatus.DRAFT, currentPolicy);
        Content firstContent = mock(Content.class);
        Content secondContent = mock(Content.class);
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(100L)).thenReturn(operator);
        when(missionService.findByMissionId(701L)).thenReturn(initialMission);
        when(couponPolicyService.findForUpdate(501L)).thenReturn(requestedPolicy);
        when(couponPolicyService.findForUpdate(502L)).thenReturn(currentPolicy);
        when(missionService.findByMissionIdForUpdate(701L)).thenReturn(lockedMission);
        when(contentService.findMissionTargetContentsForUpdate(List.of(101L, 102L), 11L))
            .thenReturn(List.of(firstContent, secondContent));
        when(missionService.replaceDraftCoreValues(
            lockedMission,
            MissionConditionType.CONTENT_SET,
            null,
            requestedPolicy,
            Instant.parse("2027-09-30T14:59:59Z")
        )).thenReturn(lockedMission);

        UpdateOperatorMissionResult result = useCase.update(
            100L,
            701L,
            command("CONTENT_SET", null, List.of(102L, 101L), 501L),
            REQUEST_ID
        );

        assertThat(result).isEqualTo(new UpdateOperatorMissionResult(701L, MissionStatus.DRAFT));
        InOrder lockOrder = inOrder(missionService, couponPolicyService, contentService);
        lockOrder.verify(missionService).findByMissionId(701L);
        lockOrder.verify(couponPolicyService).findForUpdate(501L);
        lockOrder.verify(couponPolicyService).findForUpdate(502L);
        lockOrder.verify(missionService).findByMissionIdForUpdate(701L);
        lockOrder.verify(contentService).findMissionTargetContentsForUpdate(List.of(101L, 102L), 11L);
        verify(missionTargetContentService).replaceAll(
            lockedMission,
            List.of(firstContent, secondContent)
        );
        ArgumentCaptor<AuditEventCommand> auditCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue()).satisfies(audit -> {
            assertThat(audit.previousState()).isEqualTo("DRAFT");
            assertThat(audit.nextState()).isEqualTo("DRAFT");
            assertThat(audit.result()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(audit.reasonCode()).isEqualTo("MISSION_UPDATED");
            assertThat(audit.occurredAt()).isEqualTo(NOW);
        });
        verifyNoInteractions(recordFailedAuditEventUseCase);
    }

    @Test
    void update_withVisitCount_deletesExistingTargetConnectionsWithoutLockingContents() {
        AuthorizedOperator operator = operator(100L, 11L, 900L);
        CouponPolicy policy = rewardPolicy(501L, 11L, CouponPolicyStatus.DRAFT);
        Mission mission = mission(701L, 11L, MissionStatus.DRAFT, policy);
        givenCommonUpdate(operator, mission, policy);
        when(missionService.replaceDraftCoreValues(
            mission,
            MissionConditionType.VISIT_COUNT,
            4,
            policy,
            Instant.parse("2027-09-30T14:59:59Z")
        )).thenReturn(mission);

        useCase.update(100L, 701L, command("VISIT_COUNT", 4, List.of(), 501L), REQUEST_ID);

        verifyNoInteractions(contentService);
        verify(missionTargetContentService).replaceAll(mission, List.of());
    }

    @Test
    void update_whenLockedMissionIsNotDraft_recordsFailureAndRejectsWithoutMutation() {
        AuthorizedOperator operator = operator(100L, 11L, 900L);
        CouponPolicy policy = rewardPolicy(501L, 11L, CouponPolicyStatus.DRAFT);
        Mission initialMission = mission(701L, 11L, MissionStatus.DRAFT, policy);
        Mission lockedMission = mission(701L, 11L, MissionStatus.PENDING_REVIEW, policy);
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(100L)).thenReturn(operator);
        when(missionService.findByMissionId(701L)).thenReturn(initialMission);
        when(couponPolicyService.findForUpdate(501L)).thenReturn(policy);
        when(missionService.findByMissionIdForUpdate(701L)).thenReturn(lockedMission);

        assertThatThrownBy(() -> useCase.update(
            100L,
            701L,
            command("VISIT_COUNT", 4, List.of(), 501L),
            REQUEST_ID
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.MISSION_STATE_CONFLICT);

        verifyNoInteractions(contentService, missionTargetContentService, recordAuditEventUseCase);
        ArgumentCaptor<AuditEventCommand> failureCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordFailedAuditEventUseCase).record(failureCaptor.capture());
        assertThat(failureCaptor.getValue()).satisfies(audit -> {
            assertThat(audit.previousState()).isEqualTo("PENDING_REVIEW");
            assertThat(audit.nextState()).isNull();
            assertThat(audit.result()).isEqualTo(AuditEventResult.FAILURE);
            assertThat(audit.reasonCode()).isEqualTo("MISSION_STATE_CONFLICT");
        });
    }

    @Test
    void update_whenRewardPolicyLinkChangesAfterInitialRead_rejectsAsConflict() {
        AuthorizedOperator operator = operator(100L, 11L, 900L);
        CouponPolicy initialPolicy = rewardPolicy(501L, 11L, CouponPolicyStatus.DRAFT);
        CouponPolicy changedPolicy = rewardPolicy(502L, 11L, CouponPolicyStatus.DRAFT);
        CouponPolicy requestedPolicy = rewardPolicy(503L, 11L, CouponPolicyStatus.DRAFT);
        Mission initialMission = mission(701L, 11L, MissionStatus.DRAFT, initialPolicy);
        Mission lockedMission = mission(701L, 11L, MissionStatus.DRAFT, changedPolicy);
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(100L)).thenReturn(operator);
        when(missionService.findByMissionId(701L)).thenReturn(initialMission);
        when(couponPolicyService.findForUpdate(501L)).thenReturn(initialPolicy);
        when(couponPolicyService.findForUpdate(503L)).thenReturn(requestedPolicy);
        when(missionService.findByMissionIdForUpdate(701L)).thenReturn(lockedMission);

        assertThatThrownBy(() -> useCase.update(
            100L,
            701L,
            command("VISIT_COUNT", 4, List.of(), 503L),
            REQUEST_ID
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.MISSION_STATE_CONFLICT);
    }

    @Test
    void update_whenMissionBelongsToAnotherRegion_rejectsBeforePolicyLocksAndRecordsFailure() {
        AuthorizedOperator operator = operator(100L, 11L, 900L);
        CouponPolicy policy = rewardPolicy(501L, 12L, CouponPolicyStatus.DRAFT);
        Mission mission = mission(701L, 12L, MissionStatus.DRAFT, policy);
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(100L)).thenReturn(operator);
        when(missionService.findByMissionId(701L)).thenReturn(mission);

        assertBusinessError(
            command("VISIT_COUNT", 4, List.of(), 501L),
            ErrorCode.FORBIDDEN
        );

        verifyNoInteractions(couponPolicyService, contentService, missionTargetContentService, recordAuditEventUseCase);
        verify(recordFailedAuditEventUseCase).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void update_whenRequestedRewardPolicyIsEnded_rejectsWithoutReplacingMission() {
        AuthorizedOperator operator = operator(100L, 11L, 900L);
        CouponPolicy currentPolicy = rewardPolicy(501L, 11L, CouponPolicyStatus.DRAFT);
        CouponPolicy endedPolicy = rewardPolicy(502L, 11L, CouponPolicyStatus.ENDED);
        Mission mission = mission(701L, 11L, MissionStatus.DRAFT, currentPolicy);
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(100L)).thenReturn(operator);
        when(missionService.findByMissionId(701L)).thenReturn(mission);
        when(couponPolicyService.findForUpdate(501L)).thenReturn(currentPolicy);
        when(couponPolicyService.findForUpdate(502L)).thenReturn(endedPolicy);
        when(missionService.findByMissionIdForUpdate(701L)).thenReturn(mission);

        assertBusinessError(
            command("VISIT_COUNT", 4, List.of(), 502L),
            ErrorCode.MISSION_STATE_CONFLICT
        );

        verifyNoInteractions(contentService, missionTargetContentService, recordAuditEventUseCase);
        verify(recordFailedAuditEventUseCase).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void update_whenTargetContentLookupFails_propagatesErrorWithoutReplacingConnections() {
        AuthorizedOperator operator = operator(100L, 11L, 900L);
        CouponPolicy policy = rewardPolicy(501L, 11L, CouponPolicyStatus.DRAFT);
        Mission mission = mission(701L, 11L, MissionStatus.DRAFT, policy);
        givenCommonUpdate(operator, mission, policy);
        when(contentService.findMissionTargetContentsForUpdate(List.of(101L), 11L))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        assertBusinessError(
            command("CONTENT_SET", null, List.of(101L), 501L),
            ErrorCode.NOT_FOUND
        );

        verifyNoInteractions(missionTargetContentService, recordAuditEventUseCase);
        verify(recordFailedAuditEventUseCase).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void update_withInvalidCompleteReplacement_rejectsBeforeAuthorizationOrLocks() {
        assertThatThrownBy(() -> useCase.update(
            100L,
            701L,
            command("CONTENT_SET", null, List.of(), 501L),
            REQUEST_ID
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(
            operatorAuthorizationService,
            couponPolicyService,
            missionService,
            contentService,
            missionTargetContentService,
            recordAuditEventUseCase,
            recordFailedAuditEventUseCase
        );
    }

    private void givenCommonUpdate(
        AuthorizedOperator operator,
        Mission mission,
        CouponPolicy policy
    ) {
        when(operatorAuthorizationService.requireAuthorizedOperatorForUpdate(100L)).thenReturn(operator);
        when(missionService.findByMissionId(701L)).thenReturn(mission);
        when(couponPolicyService.findForUpdate(policy.getCouponPolicyId())).thenReturn(policy);
        when(missionService.findByMissionIdForUpdate(701L)).thenReturn(mission);
    }

    private void assertBusinessError(
        UpdateOperatorMissionUseCase.UpdateOperatorMissionCommand command,
        ErrorCode errorCode
    ) {
        assertThatThrownBy(() -> useCase.update(100L, 701L, command, REQUEST_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(errorCode);
    }

    private UpdateOperatorMissionUseCase.UpdateOperatorMissionCommand command(
        String conditionType,
        Integer requiredVisitCount,
        List<Long> targetContentIds,
        Long rewardCouponPolicyId
    ) {
        return new UpdateOperatorMissionUseCase.UpdateOperatorMissionCommand(
            conditionType,
            requiredVisitCount,
            targetContentIds,
            rewardCouponPolicyId,
            OffsetDateTime.parse("2027-09-30T23:59:59+09:00")
        );
    }

    private AuthorizedOperator operator(
        Long userId,
        Long regionId,
        Long roleAssignmentId
    ) {
        AppUser user = mock(AppUser.class);
        Region region = mock(Region.class);
        UserRoleAssignment roleAssignment = mock(UserRoleAssignment.class);
        when(user.getUserId()).thenReturn(userId);
        when(user.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(region.getRegionId()).thenReturn(regionId);
        when(roleAssignment.getRoleAssignmentId()).thenReturn(roleAssignmentId);
        when(roleAssignment.getAppUser()).thenReturn(user);
        when(roleAssignment.getRole()).thenReturn(UserRole.OPERATOR);
        return new AuthorizedOperator(user, region, roleAssignment);
    }

    private CouponPolicy rewardPolicy(
        Long couponPolicyId,
        Long regionId,
        CouponPolicyStatus status
    ) {
        Region region = mock(Region.class);
        CouponPolicy couponPolicy = mock(CouponPolicy.class);
        when(region.getRegionId()).thenReturn(regionId);
        when(couponPolicy.getCouponPolicyId()).thenReturn(couponPolicyId);
        when(couponPolicy.getRegion()).thenReturn(region);
        when(couponPolicy.getIssuanceType()).thenReturn(CouponIssuanceType.MISSION_REWARD);
        when(couponPolicy.getStatus()).thenReturn(status);
        return couponPolicy;
    }

    private Mission mission(
        Long missionId,
        Long regionId,
        MissionStatus status,
        CouponPolicy rewardPolicy
    ) {
        Region region = mock(Region.class);
        Mission mission = mock(Mission.class);
        when(region.getRegionId()).thenReturn(regionId);
        when(mission.getMissionId()).thenReturn(missionId);
        when(mission.getRegion()).thenReturn(region);
        when(mission.getStatus()).thenReturn(status);
        when(mission.getRewardCouponPolicy()).thenReturn(rewardPolicy);
        return mission;
    }
}
