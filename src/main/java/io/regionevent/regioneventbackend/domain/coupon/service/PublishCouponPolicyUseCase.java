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
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class PublishCouponPolicyUseCase {

    private static final int MAXIMUM_REASON_LENGTH = 500;

    private final AppUserService appUserService;
    private final OperatorAuthorizationService operatorAuthorizationService;
    private final CouponPolicyService couponPolicyService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public PublishCouponPolicyUseCase(
        AppUserService appUserService,
        OperatorAuthorizationService operatorAuthorizationService,
        CouponPolicyService couponPolicyService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.appUserService = appUserService;
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.couponPolicyService = couponPolicyService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public PublishCouponPolicyResult publish(
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
        validateOwnership(operator, couponPolicy);

        if (couponPolicy.getStatus() == CouponPolicyStatus.PUBLISHED) {
            return PublishCouponPolicyResult.from(couponPolicy);
        }
        if (couponPolicy.getStatus() != CouponPolicyStatus.DRAFT) {
            throw new BusinessException(ErrorCode.COUPON_POLICY_CONFLICT);
        }

        Instant publishedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        CouponPolicy publishedCouponPolicy = couponPolicyService.publish(couponPolicy, publishedAt);
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            publishedCouponPolicy.getRegion(),
            AuditEventTargetType.COUPON_POLICY,
            publishedCouponPolicy.getCouponPolicyId(),
            CouponPolicyStatus.DRAFT.name(),
            CouponPolicyStatus.PUBLISHED.name(),
            AuditEventResult.SUCCESS,
            null,
            normalizedReason,
            null,
            new AuditEventActor(operator.roleAssignment()),
            publishedAt
        ));
        return PublishCouponPolicyResult.from(publishedCouponPolicy);
    }

    private void validateOwnership(
        AuthorizedOperator operator,
        CouponPolicy couponPolicy
    ) {
        Content content = couponPolicy.getContent();
        if (!content.isOwnedBy(operator.user().getUserId())
            || !content.isScopedTo(operator.region().getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
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
