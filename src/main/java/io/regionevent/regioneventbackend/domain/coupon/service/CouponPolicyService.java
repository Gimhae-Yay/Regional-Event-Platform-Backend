package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CouponPolicyService {

    private final CouponPolicyRepository couponPolicyRepository;

    public CouponPolicyService(CouponPolicyRepository couponPolicyRepository) {
        this.couponPolicyRepository = couponPolicyRepository;
    }

    public CouponPolicy create(CreateCouponPolicyCommand command) {
        CouponPolicy couponPolicy = new CouponPolicy(
            command.content(),
            command.region(),
            command.name(),
            command.description(),
            command.issueSourceType(),
            command.discountAmount(),
            command.minimumPaymentAmount(),
            command.validDaysAfterIssue(),
            command.issueStartsAt(),
            command.issueEndsAt(),
            command.totalIssueLimit()
        );
        return couponPolicyRepository.saveAndFlush(couponPolicy);
    }

    public CouponPolicy findForUpdate(Long couponPolicyId) {
        validateRequiredId(couponPolicyId);
        return couponPolicyRepository.findByCouponPolicyIdForUpdate(couponPolicyId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public CouponPolicy publish(
        CouponPolicy couponPolicy,
        Instant publishedAt
    ) {
        couponPolicy.publish(publishedAt);
        return couponPolicyRepository.saveAndFlush(couponPolicy);
    }

    public record CreateCouponPolicyCommand(
        Content content,
        Region region,
        String name,
        String description,
        CouponIssuanceType issueSourceType,
        long discountAmount,
        long minimumPaymentAmount,
        int validDaysAfterIssue,
        Instant issueStartsAt,
        Instant issueEndsAt,
        Long totalIssueLimit
    ) {
    }

    @Transactional(readOnly = true)
    public CouponPolicy findStampbookRewardPolicy(
        Long couponPolicyId,
        Long regionId
    ) {
        validateRequiredId(couponPolicyId);
        validateRequiredId(regionId);

        CouponPolicy couponPolicy = couponPolicyRepository.findByCouponPolicyId(couponPolicyId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!couponPolicy.getRegion().getRegionId().equals(regionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (couponPolicy.getIssuanceType() != CouponIssuanceType.STAMPBOOK_COMPLETION) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return couponPolicy;
    }

    @Transactional
    public CouponPolicy findForIssue(Long couponPolicyId) {
        return couponPolicyRepository.findByCouponPolicyIdForUpdate(couponPolicyId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public void issue(
        CouponPolicy couponPolicy,
        CouponIssuanceType issueSourceType,
        Instant issuedAt
    ) {
        if (couponPolicy.getIssuanceType() != issueSourceType) {
            throw new BusinessException(ErrorCode.COUPON_ISSUE_CONFLICT);
        }
        if (couponPolicy.getStatus() != CouponPolicyStatus.PUBLISHED
            || issuedAt.isBefore(couponPolicy.getIssueStartsAt())
            || issuedAt.isAfter(couponPolicy.getIssueEndsAt())) {
            throw new BusinessException(ErrorCode.COUPON_POLICY_NOT_PUBLISHED);
        }
        try {
            couponPolicy.issue(issuedAt);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.COUPON_ISSUE_CONFLICT, exception);
        }
    }

    private void validateRequiredId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
