package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
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
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final Clock clock;

    public SubmitOperatorMissionUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        CouponPolicyService couponPolicyService,
        MissionService missionService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        Clock clock
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.couponPolicyService = couponPolicyService;
        this.missionService = missionService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public SubmitOperatorMissionResult submit(
        Long userId,
        Long missionId,
        UUID requestId
    ) {
        validateCommand(userId, missionId, requestId);
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperatorForUpdate(userId);
        MissionUpdateSnapshot initialSnapshot = missionService.findUpdateSnapshot(missionId);
        Mission mission = null;
        MissionStatus previousState = initialSnapshot.getStatus();

        try {
            validateRegionScope(operator, initialSnapshot.getRegion());
            Long initialRewardCouponPolicyId = initialSnapshot.getRewardCouponPolicyId();
            CouponPolicy rewardCouponPolicy = couponPolicyService.findForUpdate(initialRewardCouponPolicyId);
            mission = missionService.findByMissionIdForUpdate(missionId);
            previousState = mission.getStatus();
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
        } catch (BusinessException exception) {
            Region auditRegion = mission == null ? initialSnapshot.getRegion() : mission.getRegion();
            recordFailure(requestId, auditRegion, missionId, previousState, operator, exception.getErrorCode());
            throw exception;
        } catch (RuntimeException exception) {
            Region auditRegion = mission == null ? initialSnapshot.getRegion() : mission.getRegion();
            recordFailure(
                requestId,
                auditRegion,
                missionId,
                previousState,
                operator,
                ErrorCode.INTERNAL_SERVER_ERROR
            );
            throw exception;
        }
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
        if (!mission.getRegion().getRegionId().equals(rewardCouponPolicy.getRegion().getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (rewardCouponPolicy.getIssuanceType() != CouponIssuanceType.MISSION_REWARD
            || (rewardCouponPolicy.getStatus() != CouponPolicyStatus.DRAFT
                && rewardCouponPolicy.getStatus() != CouponPolicyStatus.PUBLISHED)
            || mission.getStatus() != MissionStatus.DRAFT) {
            throw new BusinessException(ErrorCode.MISSION_STATE_CONFLICT);
        }
    }

    private void validateRegionScope(
        AuthorizedOperator operator,
        Region region
    ) {
        if (!operator.region().getRegionId().equals(region.getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void recordFailure(
        UUID requestId,
        Region region,
        Long missionId,
        MissionStatus previousState,
        AuthorizedOperator operator,
        ErrorCode errorCode
    ) {
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            region,
            AuditEventTargetType.MISSION,
            missionId,
            previousState.name(),
            null,
            AuditEventResult.FAILURE,
            errorCode.code(),
            new AuditEventActor(operator.roleAssignment()),
            clock.instant().truncatedTo(ChronoUnit.MICROS)
        ));
    }
}
