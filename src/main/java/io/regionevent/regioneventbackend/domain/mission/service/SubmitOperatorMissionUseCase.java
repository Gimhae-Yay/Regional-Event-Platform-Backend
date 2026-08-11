package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponPolicyService;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class SubmitOperatorMissionUseCase {

    private static final String SUCCESS_REASON_CODE = "MISSION_SUBMITTED";

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final CouponPolicyService couponPolicyService;
    private final MissionService missionService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public SubmitOperatorMissionUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        CouponPolicyService couponPolicyService,
        MissionService missionService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.couponPolicyService = couponPolicyService;
        this.missionService = missionService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public SubmitOperatorMissionResult submit(
        Long userId,
        Long missionId,
        UUID requestId
    ) {
        validateCommand(userId, missionId, requestId);
        Long initialRewardCouponPolicyId = missionService.findRewardCouponPolicyId(missionId);

        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperatorForUpdate(userId);
        CouponPolicy rewardCouponPolicy = couponPolicyService.findForUpdate(initialRewardCouponPolicyId);
        Mission mission = missionService.findByMissionIdForUpdate(missionId);
        validateLockedMission(operator, mission, rewardCouponPolicy, initialRewardCouponPolicyId);

        Mission submittedMission = missionService.submitForReview(mission);
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            submittedMission.getRegion(),
            AuditEventTargetType.MISSION,
            submittedMission.getMissionId(),
            MissionStatus.DRAFT.name(),
            MissionStatus.PENDING_REVIEW.name(),
            AuditEventResult.SUCCESS,
            SUCCESS_REASON_CODE,
            new AuditEventActor(operator.roleAssignment()),
            clock.instant()
        ));
        return SubmitOperatorMissionResult.from(submittedMission);
    }

    private void validateCommand(
        Long userId,
        Long missionId,
        UUID requestId
    ) {
        if (userId == null || userId <= 0 || missionId == null || missionId <= 0 || requestId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateLockedMission(
        AuthorizedOperator operator,
        Mission mission,
        CouponPolicy rewardCouponPolicy,
        Long initialRewardCouponPolicyId
    ) {
        if (!mission.getRewardCouponPolicy().getCouponPolicyId().equals(initialRewardCouponPolicyId)) {
            throw new BusinessException(ErrorCode.MISSION_STATE_CONFLICT);
        }
        if (!operator.region().getRegionId().equals(mission.getRegion().getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!mission.getRegion().getRegionId().equals(rewardCouponPolicy.getRegion().getRegionId())
            || rewardCouponPolicy.getIssuanceType() != CouponIssuanceType.MISSION_REWARD
            || (rewardCouponPolicy.getStatus() != CouponPolicyStatus.DRAFT
                && rewardCouponPolicy.getStatus() != CouponPolicyStatus.PUBLISHED)
            || mission.getStatus() != MissionStatus.DRAFT) {
            throw new BusinessException(ErrorCode.MISSION_STATE_CONFLICT);
        }
    }
}
