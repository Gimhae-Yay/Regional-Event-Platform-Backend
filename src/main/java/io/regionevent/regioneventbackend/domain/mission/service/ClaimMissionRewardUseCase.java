package io.regionevent.regioneventbackend.domain.mission.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
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
import io.regionevent.regioneventbackend.domain.coupon.service.CouponIssuanceHasher;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponIssuanceService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponPolicyService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionRewardClaim;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ClaimMissionRewardUseCase {

    private final FindMissionRewardClaimResultUseCase findMissionRewardClaimResultUseCase;
    private final AppUserService appUserService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final MissionParticipationReadService missionParticipationReadService;
    private final MissionParticipationService missionParticipationService;
    private final MissionService missionService;
    private final CouponPolicyService couponPolicyService;
    private final MissionRewardClaimService missionRewardClaimService;
    private final CouponService couponService;
    private final CouponIssuanceService couponIssuanceService;
    private final CouponStatusHistoryService couponStatusHistoryService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final TransactionTemplate transactionTemplate;

    public ClaimMissionRewardUseCase(
        FindMissionRewardClaimResultUseCase findMissionRewardClaimResultUseCase,
        AppUserService appUserService,
        UserRoleAssignmentService userRoleAssignmentService,
        MissionParticipationReadService missionParticipationReadService,
        MissionParticipationService missionParticipationService,
        MissionService missionService,
        CouponPolicyService couponPolicyService,
        MissionRewardClaimService missionRewardClaimService,
        CouponService couponService,
        CouponIssuanceService couponIssuanceService,
        CouponStatusHistoryService couponStatusHistoryService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        PlatformTransactionManager transactionManager
    ) {
        this.findMissionRewardClaimResultUseCase = findMissionRewardClaimResultUseCase;
        this.appUserService = appUserService;
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.missionParticipationReadService = missionParticipationReadService;
        this.missionParticipationService = missionParticipationService;
        this.missionService = missionService;
        this.couponPolicyService = couponPolicyService;
        this.missionRewardClaimService = missionRewardClaimService;
        this.couponService = couponService;
        this.couponIssuanceService = couponIssuanceService;
        this.couponStatusHistoryService = couponStatusHistoryService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ClaimMissionRewardResult claim(Long userId, Long participationId, UUID requestId) {
        userRoleAssignmentService.findActiveVisitor(userId);
        MissionParticipation participation = missionParticipationReadService.findDetail(participationId);
        validateOwnership(userId, participation);

        ClaimMissionRewardResult existingResult = findMissionRewardClaimResultUseCase.find(participationId).orElse(null);
        if (existingResult != null) {
            return existingResult;
        }

        Long missionId = participation.getMission().getMissionId();
        Long couponPolicyId = missionService.findRewardCouponPolicyId(missionId);

        try {
            return transactionTemplate.execute(status -> claimWithinTransaction(
                userId,
                missionId,
                couponPolicyId,
                participationId,
                requestId
            ));
        } catch (DataIntegrityViolationException exception) {
            return findMissionRewardClaimResultUseCase.find(participationId).orElseThrow(() -> exception);
        }
    }

    private ClaimMissionRewardResult claimWithinTransaction(
        Long userId,
        Long missionId,
        Long couponPolicyId,
        Long participationId,
        UUID requestId
    ) {
        AppUser user = appUserService.findActiveUserForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        CouponPolicy couponPolicy = couponPolicyService.findForUpdate(couponPolicyId);
        Mission mission = missionService.findByMissionIdForUpdate(missionId);
        MissionParticipation participation = missionParticipationService.findForUpdate(participationId);
        validateOwnership(userId, participation);

        ClaimMissionRewardResult existingResult = findExistingResult(participationId);
        if (existingResult != null) {
            return existingResult;
        }

        Instant operationAt = missionService.findCurrentDatabaseTime();
        try {
            validateClaimable(mission, participation, couponPolicy, operationAt);
            couponPolicyService.issue(couponPolicy, CouponIssuanceType.MISSION_REWARD, operationAt);
        } catch (BusinessException exception) {
            BusinessException conflict = new BusinessException(ErrorCode.MISSION_REWARD_CLAIM_CONFLICT, exception);
            recordFailure(requestId, couponPolicy, user, operationAt, conflict.getErrorCode());
            throw conflict;
        }

        try {
            MissionRewardClaim claim = missionRewardClaimService.create(
                new MissionRewardClaim(participation, couponPolicy, operationAt)
            );
            Coupon coupon = couponService.create(new Coupon(
                couponPolicy,
                user,
                operationAt,
                operationAt.plus(couponPolicy.getValidDays(), ChronoUnit.DAYS)
            ));
            couponIssuanceService.create(new CouponIssuance(
                coupon,
                couponPolicy,
                user,
                null,
                claim,
                null,
                CouponIssuanceHasher.hashMissionRewardIssue(couponPolicyId, user.getUserId(), claim.getMissionRewardClaimId()),
                operationAt
            ));
            couponStatusHistoryService.create(new CouponStatusHistory(
                coupon, null, CouponStatus.AVAILABLE, "MISSION_REWARD_ISSUED", "USER", operationAt
            ));
            recordAuditEventUseCase.record(new AuditEventCommand(
                requestId,
                couponPolicy.getRegion(),
                AuditEventTargetType.COUPON,
                coupon.getCouponId(),
                null,
                CouponStatus.AVAILABLE.name(),
                AuditEventResult.SUCCESS,
                "COUPON_ISSUED",
                "MISSION_REWARD_CLAIM:" + claim.getMissionRewardClaimId(),
                new AuditEventActor(user, UserRole.VISITOR),
                operationAt
            ));
            return ClaimMissionRewardResult.from(claim, coupon);
        } catch (DataIntegrityViolationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            recordFailure(requestId, couponPolicy, user, operationAt, ErrorCode.INTERNAL_SERVER_ERROR);
            throw exception;
        }
    }

    private void recordFailure(
        UUID requestId,
        CouponPolicy couponPolicy,
        AppUser user,
        Instant operationAt,
        ErrorCode errorCode
    ) {
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            couponPolicy.getRegion(),
            AuditEventTargetType.COUPON,
            null,
            couponPolicy.getStatus().name(),
            null,
            AuditEventResult.FAILURE,
            errorCode.code(),
            new AuditEventActor(user, UserRole.VISITOR),
            operationAt
        ));
    }

    private ClaimMissionRewardResult findExistingResult(Long participationId) {
        return missionRewardClaimService.findByParticipationIdForUpdate(participationId)
            .flatMap(claim -> couponIssuanceService.findByMissionRewardClaimId(claim.getMissionRewardClaimId())
                .filter(issuance -> couponStatusHistoryService.findMissionRewardInitialByCouponId(
                    issuance.getCoupon().getCouponId()
                ).isPresent())
                .map(issuance -> ClaimMissionRewardResult.from(claim, issuance.getCoupon())))
            .orElse(null);
    }

    private void validateOwnership(Long userId, MissionParticipation participation) {
        if (participation.getUser() == null || !userId.equals(participation.getUser().getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateClaimable(
        Mission mission,
        MissionParticipation participation,
        CouponPolicy couponPolicy,
        Instant operationAt
    ) {
        if (participation.getStatus() != MissionParticipationStatus.COMPLETED
            || mission.getStatus() != MissionStatus.PUBLISHED
            || !mission.getEndsAt().isAfter(operationAt)
            || !couponPolicy.getCouponPolicyId().equals(mission.getRewardCouponPolicy().getCouponPolicyId())
            || !couponPolicy.getRegion().getRegionId().equals(mission.getRegion().getRegionId())
            || couponPolicy.getIssuanceType() != CouponIssuanceType.MISSION_REWARD
            || couponPolicy.getStatus() != CouponPolicyStatus.PUBLISHED
            || operationAt.isBefore(couponPolicy.getIssueStartsAt())
            || operationAt.isAfter(couponPolicy.getIssueEndsAt())) {
            throw new BusinessException(ErrorCode.MISSION_REWARD_CLAIM_CONFLICT);
        }
    }
}
