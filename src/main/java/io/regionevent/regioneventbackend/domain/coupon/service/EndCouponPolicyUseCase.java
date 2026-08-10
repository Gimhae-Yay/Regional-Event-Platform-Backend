package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Clock;
import java.time.Instant;
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
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.mission.service.MissionService;
import io.regionevent.regioneventbackend.domain.stampbook.service.StampbookService;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class EndCouponPolicyUseCase {

    private static final int MAXIMUM_REASON_LENGTH = 500;

    private final AppUserService appUserService;
    private final OperatorAuthorizationService operatorAuthorizationService;
    private final CouponPolicyService couponPolicyService;
    private final MissionService missionService;
    private final StampbookService stampbookService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final Clock clock;

    public EndCouponPolicyUseCase(
        AppUserService appUserService,
        OperatorAuthorizationService operatorAuthorizationService,
        CouponPolicyService couponPolicyService,
        MissionService missionService,
        StampbookService stampbookService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        Clock clock
    ) {
        this.appUserService = appUserService;
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.couponPolicyService = couponPolicyService;
        this.missionService = missionService;
        this.stampbookService = stampbookService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public EndCouponPolicyResult end(
        Long userId,
        Long couponPolicyId,
        String reason,
        UUID requestId
    ) {
        String normalizedReason = normalizeReason(reason);
        appUserService.findActiveUserForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperatorForUpdate(userId);
        CouponPolicy couponPolicy = couponPolicyService.findForUpdate(couponPolicyId);
        validateOwnership(requestId, operator, couponPolicy);

        if (couponPolicy.getStatus() == CouponPolicyStatus.ENDED) {
            return EndCouponPolicyResult.from(couponPolicy);
        }
        if (couponPolicy.getStatus() != CouponPolicyStatus.PUBLISHED) {
            recordFailure(requestId, operator, couponPolicy, ErrorCode.COUPON_POLICY_CONFLICT);
            throw new BusinessException(ErrorCode.COUPON_POLICY_CONFLICT);
        }
        validateNotReferenced(requestId, operator, couponPolicy);

        Instant endedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        CouponPolicy endedCouponPolicy = couponPolicyService.end(couponPolicy, endedAt);
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            endedCouponPolicy.getRegion(),
            AuditEventTargetType.COUPON_POLICY,
            endedCouponPolicy.getCouponPolicyId(),
            CouponPolicyStatus.PUBLISHED.name(),
            CouponPolicyStatus.ENDED.name(),
            AuditEventResult.SUCCESS,
            null,
            normalizedReason,
            null,
            new AuditEventActor(operator.roleAssignment()),
            endedAt
        ));
        return EndCouponPolicyResult.from(endedCouponPolicy);
    }

    private void validateOwnership(
        UUID requestId,
        AuthorizedOperator operator,
        CouponPolicy couponPolicy
    ) {
        Content content = couponPolicy.getContent();
        if (!content.isOwnedBy(operator.user().getUserId())
            || !content.isScopedTo(operator.region().getRegionId())) {
            recordFailure(requestId, operator, couponPolicy, ErrorCode.FORBIDDEN);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateNotReferenced(
        UUID requestId,
        AuthorizedOperator operator,
        CouponPolicy couponPolicy
    ) {
        if (missionService.existsPublishedRewardCouponPolicy(couponPolicy.getCouponPolicyId())
            || stampbookService.existsPublishedRewardCouponPolicy(couponPolicy.getCouponPolicyId())) {
            recordFailure(requestId, operator, couponPolicy, ErrorCode.COUPON_POLICY_REFERENCED);
            throw new BusinessException(ErrorCode.COUPON_POLICY_REFERENCED);
        }
    }

    private void recordFailure(
        UUID requestId,
        AuthorizedOperator operator,
        CouponPolicy couponPolicy,
        ErrorCode errorCode
    ) {
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            couponPolicy.getRegion(),
            AuditEventTargetType.COUPON_POLICY,
            couponPolicy.getCouponPolicyId(),
            couponPolicy.getStatus().name(),
            null,
            AuditEventResult.FAILURE,
            errorCode.code(),
            new AuditEventActor(operator.roleAssignment()),
            clock.instant().truncatedTo(ChronoUnit.MICROS)
        ));
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        String normalizedReason = reason.strip();
        if (normalizedReason.isEmpty() || normalizedReason.length() > MAXIMUM_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return normalizedReason;
    }
}
