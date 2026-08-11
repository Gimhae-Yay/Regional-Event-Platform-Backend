package io.regionevent.regioneventbackend.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuance;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponIssuanceService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponPolicyService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionRewardClaim;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class ClaimMissionRewardUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long PARTICIPATION_ID = 701L;
    private static final Long MISSION_ID = 501L;
    private static final Long POLICY_ID = 301L;
    private static final Instant OPERATION_AT = Instant.parse("2026-08-11T00:00:00Z");
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000624");

    private final FindMissionRewardClaimResultUseCase findMissionRewardClaimResultUseCase = mock(
        FindMissionRewardClaimResultUseCase.class
    );
    private final UserRoleAssignmentService roleAssignmentService = mock(UserRoleAssignmentService.class);
    private final MissionParticipationReadService participationReadService = mock(MissionParticipationReadService.class);
    private final MissionParticipationService participationService = mock(MissionParticipationService.class);
    private final MissionService missionService = mock(MissionService.class);
    private final CouponPolicyService couponPolicyService = mock(CouponPolicyService.class);
    private final MissionRewardClaimService claimService = mock(MissionRewardClaimService.class);
    private final CouponService couponService = mock(CouponService.class);
    private final CouponIssuanceService issuanceService = mock(CouponIssuanceService.class);
    private final CouponStatusHistoryService statusHistoryService = mock(CouponStatusHistoryService.class);
    private final RecordAuditEventUseCase auditUseCase = mock(RecordAuditEventUseCase.class);
    private final RecordFailedAuditEventUseCase failedAuditUseCase = mock(RecordFailedAuditEventUseCase.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final ClaimMissionRewardUseCase useCase = new ClaimMissionRewardUseCase(
        findMissionRewardClaimResultUseCase,
        roleAssignmentService,
        participationReadService,
        participationService,
        missionService,
        couponPolicyService,
        claimService,
        couponService,
        issuanceService,
        statusHistoryService,
        auditUseCase,
        failedAuditUseCase,
        transactionManager
    );

    private final AppUser user = mock(AppUser.class);
    private final UserRoleAssignment visitor = mock(UserRoleAssignment.class);
    private final Mission initialMission = mock(Mission.class);
    private final MissionParticipation initialParticipation = mock(MissionParticipation.class);

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
            .thenReturn(mock(TransactionStatus.class));
        when(roleAssignmentService.findActiveVisitor(USER_ID)).thenReturn(visitor);
        when(user.getUserId()).thenReturn(USER_ID);
        when(user.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(initialMission.getMissionId()).thenReturn(MISSION_ID);
        when(initialParticipation.getMissionParticipationId()).thenReturn(PARTICIPATION_ID);
        when(initialParticipation.getMission()).thenReturn(initialMission);
        when(initialParticipation.getUser()).thenReturn(user);
        when(participationReadService.findDetail(PARTICIPATION_ID)).thenReturn(initialParticipation);
        when(findMissionRewardClaimResultUseCase.find(PARTICIPATION_ID)).thenReturn(Optional.empty());
        when(missionService.findRewardCouponPolicyId(MISSION_ID)).thenReturn(POLICY_ID);
    }

    @Test
    void claim_기존결과가있으면잠금과검증없이반환한다() {
        ClaimMissionRewardResult existing = result(9001L, 8001L);
        when(findMissionRewardClaimResultUseCase.find(PARTICIPATION_ID)).thenReturn(Optional.of(existing));

        ClaimMissionRewardResult result = useCase.claim(USER_ID, PARTICIPATION_ID, REQUEST_ID);

        assertThat(result).isEqualTo(existing);
        verifyNoInteractions(couponPolicyService, claimService, couponService, issuanceService, statusHistoryService);
        verify(missionService, never()).findCurrentDatabaseTime();
    }

    @Test
    void claim_신규수령이면정책_미션_참여순서로잠그고DB시각을한번만사용한다() {
        SuccessFixture fixture = prepareSuccess();

        ClaimMissionRewardResult result = useCase.claim(USER_ID, PARTICIPATION_ID, REQUEST_ID);

        assertThat(result.missionRewardClaimId()).isEqualTo(9001L);
        assertThat(result.couponId()).isEqualTo(8001L);
        InOrder locks = inOrder(couponPolicyService, missionService, participationService);
        locks.verify(couponPolicyService).findForUpdate(POLICY_ID);
        locks.verify(missionService).findByMissionIdForUpdate(MISSION_ID);
        locks.verify(participationService).findForUpdate(PARTICIPATION_ID);
        locks.verify(missionService).findCurrentDatabaseTime();
        verify(missionService).findCurrentDatabaseTime();
        verify(couponPolicyService).issue(fixture.policy(), CouponIssuanceType.MISSION_REWARD, OPERATION_AT);
    }

    @Test
    void claim_잠금후참여자가변경되면소유권을다시검증한다() {
        SuccessFixture fixture = prepareSuccess();
        AppUser otherUser = mock(AppUser.class);
        when(otherUser.getUserId()).thenReturn(USER_ID + 1);
        when(fixture.participation().getUser()).thenReturn(otherUser);

        assertThatThrownBy(() -> useCase.claim(USER_ID, PARTICIPATION_ID, REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(missionService, never()).findCurrentDatabaseTime();
        verifyNoInteractions(claimService, couponService, issuanceService, statusHistoryService, auditUseCase);
    }

    @Test
    void claim_신규수령의영속시각_만료_상태이력_감사값은계약과같다() {
        prepareSuccess();

        useCase.claim(USER_ID, PARTICIPATION_ID, REQUEST_ID);

        ArgumentCaptor<MissionRewardClaim> claimCaptor = ArgumentCaptor.forClass(MissionRewardClaim.class);
        ArgumentCaptor<Coupon> couponCaptor = ArgumentCaptor.forClass(Coupon.class);
        ArgumentCaptor<CouponIssuance> issuanceCaptor = ArgumentCaptor.forClass(CouponIssuance.class);
        ArgumentCaptor<CouponStatusHistory> historyCaptor = ArgumentCaptor.forClass(CouponStatusHistory.class);
        ArgumentCaptor<AuditEventCommand> auditCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(claimService).create(claimCaptor.capture());
        verify(couponService).create(couponCaptor.capture());
        verify(issuanceService).create(issuanceCaptor.capture());
        verify(statusHistoryService).create(historyCaptor.capture());
        verify(auditUseCase).record(auditCaptor.capture());

        assertThat(claimCaptor.getValue().getClaimedAt()).isEqualTo(OPERATION_AT);
        assertThat(couponCaptor.getValue().getIssuedAt()).isEqualTo(OPERATION_AT);
        assertThat(couponCaptor.getValue().getExpiresAt()).isEqualTo(OPERATION_AT.plusSeconds(7 * 86_400L));
        assertThat(issuanceCaptor.getValue().getIssuedAt()).isEqualTo(OPERATION_AT);
        assertThat(historyCaptor.getValue().getOccurredAt()).isEqualTo(OPERATION_AT);
        assertThat(historyCaptor.getValue().getPreviousStatus()).isNull();
        assertThat(historyCaptor.getValue().getNextStatus()).isEqualTo(CouponStatus.AVAILABLE);
        assertThat(historyCaptor.getValue().getReasonCode()).isEqualTo("MISSION_REWARD_ISSUED");
        assertThat(historyCaptor.getValue().getActorKind()).isEqualTo("USER");
        assertThat(auditCaptor.getValue().occurredAt()).isEqualTo(OPERATION_AT);
        assertThat(auditCaptor.getValue().reasonCode()).isEqualTo("COUPON_ISSUED");
        assertThat(auditCaptor.getValue().evidenceReference()).isEqualTo("MISSION_REWARD_CLAIM:9001");
    }

    @Test
    void claim_종료경계와참여상태와정책불일치는같은충돌오류다() {
        SuccessFixture fixture = prepareSuccess();
        when(fixture.mission().getEndsAt()).thenReturn(OPERATION_AT);
        assertConflict();

        when(fixture.mission().getEndsAt()).thenReturn(OPERATION_AT.plusSeconds(1));
        when(fixture.participation().getStatus()).thenReturn(MissionParticipationStatus.IN_PROGRESS);
        assertConflict();

        when(fixture.participation().getStatus()).thenReturn(MissionParticipationStatus.COMPLETED);
        when(fixture.policy().getIssuanceType()).thenReturn(CouponIssuanceType.VISIT);
        assertConflict();
    }

    @Test
    void claim_유일제약충돌이면롤백후최초완결결과를반환한다() {
        prepareSuccess();
        ClaimMissionRewardResult winner = result(9001L, 8001L);
        when(claimService.create(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(findMissionRewardClaimResultUseCase.find(PARTICIPATION_ID))
            .thenReturn(Optional.empty(), Optional.of(winner));

        ClaimMissionRewardResult result = useCase.claim(USER_ID, PARTICIPATION_ID, REQUEST_ID);

        assertThat(result).isEqualTo(winner);
        InOrder recovery = inOrder(transactionManager, findMissionRewardClaimResultUseCase);
        recovery.verify(transactionManager).rollback(any(TransactionStatus.class));
        recovery.verify(findMissionRewardClaimResultUseCase).find(PARTICIPATION_ID);
    }

    @Test
    void claim_업무충돌이면공개오류코드로실패감사를기록한다() {
        SuccessFixture fixture = prepareSuccess();
        when(fixture.mission().getEndsAt()).thenReturn(OPERATION_AT);

        assertConflict();

        ArgumentCaptor<AuditEventCommand> auditCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(failedAuditUseCase).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().reasonCode()).isEqualTo(ErrorCode.MISSION_REWARD_CLAIM_CONFLICT.code());
        assertThat(auditCaptor.getValue().occurredAt()).isEqualTo(OPERATION_AT);
    }

    private SuccessFixture prepareSuccess() {
        Region region = mock(Region.class);
        CouponPolicy policy = mock(CouponPolicy.class);
        Mission mission = mock(Mission.class);
        MissionParticipation participation = mock(MissionParticipation.class);
        MissionRewardClaim savedClaim = mock(MissionRewardClaim.class);
        Coupon savedCoupon = mock(Coupon.class);

        when(region.getRegionId()).thenReturn(10L);
        when(policy.getCouponPolicyId()).thenReturn(POLICY_ID);
        when(policy.getRegion()).thenReturn(region);
        when(policy.getIssuanceType()).thenReturn(CouponIssuanceType.MISSION_REWARD);
        when(policy.getStatus()).thenReturn(CouponPolicyStatus.PUBLISHED);
        when(policy.getIssueStartsAt()).thenReturn(OPERATION_AT.minusSeconds(1));
        when(policy.getIssueEndsAt()).thenReturn(OPERATION_AT.plusSeconds(1));
        when(policy.getValidDays()).thenReturn(7);
        when(mission.getMissionId()).thenReturn(MISSION_ID);
        when(mission.getRegion()).thenReturn(region);
        when(mission.getRewardCouponPolicy()).thenReturn(policy);
        when(mission.getStatus()).thenReturn(MissionStatus.PUBLISHED);
        when(mission.getEndsAt()).thenReturn(OPERATION_AT.plusSeconds(1));
        when(participation.getMissionParticipationId()).thenReturn(PARTICIPATION_ID);
        when(participation.getMission()).thenReturn(mission);
        when(participation.getUser()).thenReturn(user);
        when(participation.getStatus()).thenReturn(MissionParticipationStatus.COMPLETED);
        when(couponPolicyService.findForUpdate(POLICY_ID)).thenReturn(policy);
        when(missionService.findByMissionIdForUpdate(MISSION_ID)).thenReturn(mission);
        when(participationService.findForUpdate(PARTICIPATION_ID)).thenReturn(participation);
        when(claimService.findByParticipationIdForUpdate(PARTICIPATION_ID)).thenReturn(Optional.empty());
        when(missionService.findCurrentDatabaseTime()).thenReturn(OPERATION_AT);
        when(savedClaim.getMissionRewardClaimId()).thenReturn(9001L);
        when(savedClaim.getMissionParticipation()).thenReturn(participation);
        when(savedClaim.getCouponPolicy()).thenReturn(policy);
        when(savedClaim.getClaimedAt()).thenReturn(OPERATION_AT);
        when(claimService.create(any())).thenReturn(savedClaim);
        when(savedCoupon.getCouponId()).thenReturn(8001L);
        when(savedCoupon.getCouponPolicy()).thenReturn(policy);
        when(savedCoupon.getUser()).thenReturn(user);
        when(couponService.create(any())).thenReturn(savedCoupon);
        return new SuccessFixture(policy, mission, participation);
    }

    private void assertConflict() {
        assertThatThrownBy(() -> useCase.claim(USER_ID, PARTICIPATION_ID, REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MISSION_REWARD_CLAIM_CONFLICT)
            );
    }

    private ClaimMissionRewardResult result(Long claimId, Long couponId) {
        return new ClaimMissionRewardResult(claimId, PARTICIPATION_ID, couponId, POLICY_ID, OPERATION_AT);
    }

    private record SuccessFixture(
        CouponPolicy policy,
        Mission mission,
        MissionParticipation participation
    ) {
    }
}
