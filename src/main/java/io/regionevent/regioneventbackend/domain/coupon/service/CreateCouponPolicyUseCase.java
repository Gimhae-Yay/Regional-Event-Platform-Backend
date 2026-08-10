package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.coupon.dto.CreateCouponPolicyRequest;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponPolicyService.CreateCouponPolicyCommand;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CreateCouponPolicyUseCase {

    private static final int MAXIMUM_NAME_LENGTH = 255;
    private static final int MAXIMUM_DESCRIPTION_LENGTH = 1_000;
    private static final int MINIMUM_VALID_DAYS = 1;
    private static final int MAXIMUM_VALID_DAYS = 365;

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final AppUserService appUserService;
    private final ContentService contentService;
    private final CouponPolicyService couponPolicyService;
    private final Clock clock;

    public CreateCouponPolicyUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        AppUserService appUserService,
        ContentService contentService,
        CouponPolicyService couponPolicyService,
        Clock clock
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.appUserService = appUserService;
        this.contentService = contentService;
        this.couponPolicyService = couponPolicyService;
        this.clock = clock;
    }

    @Transactional
    public CreateCouponPolicyResult create(
        Long userId,
        Long contentId,
        CreateCouponPolicyRequest request
    ) {
        CouponIssuanceType issueSourceType = validateRequest(request);
        appUserService.findActiveUserForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperatorForUpdate(userId);
        Content content = contentService.findOwnedContentForRevisionCreation(
            contentId,
            operator.user().getUserId(),
            operator.region().getRegionId()
        );
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        CouponPolicy couponPolicy = couponPolicyService.create(new CreateCouponPolicyCommand(
            content,
            content.getRegion(),
            request.name().strip(),
            request.description(),
            issueSourceType,
            request.discountAmount(),
            request.minimumPaymentAmount(),
            request.validDaysAfterIssue(),
            request.issueStartsAt(),
            request.issueEndsAt(),
            request.totalIssueLimit()
        ));
        return CreateCouponPolicyResult.from(couponPolicy, createdAt);
    }

    private CouponIssuanceType validateRequest(CreateCouponPolicyRequest request) {
        if (request == null
            || request.discountAmount() == null
            || request.minimumPaymentAmount() == null
            || request.validDaysAfterIssue() == null
            || request.issueStartsAt() == null
            || request.issueEndsAt() == null
            || request.name() == null
            || request.name().isBlank()
            || request.name().strip().length() > MAXIMUM_NAME_LENGTH
            || request.description() != null && request.description().length() > MAXIMUM_DESCRIPTION_LENGTH
            || request.issueSourceType() == null
            || request.issueSourceType().isBlank()
            || request.discountAmount() < 1
            || request.minimumPaymentAmount() < 0
            || request.validDaysAfterIssue() < MINIMUM_VALID_DAYS
            || request.validDaysAfterIssue() > MAXIMUM_VALID_DAYS
            || request.totalIssueLimit() != null && request.totalIssueLimit() < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (request.minimumPaymentAmount() < request.discountAmount()
            || !request.issueStartsAt().isBefore(request.issueEndsAt())) {
            throw new BusinessException(ErrorCode.COUPON_POLICY_CONFLICT);
        }
        return toIssueSourceType(request.issueSourceType());
    }

    private CouponIssuanceType toIssueSourceType(String value) {
        try {
            return CouponIssuanceType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
        }
    }
}
