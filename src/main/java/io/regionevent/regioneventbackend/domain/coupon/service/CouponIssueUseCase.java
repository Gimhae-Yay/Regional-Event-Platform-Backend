package io.regionevent.regioneventbackend.domain.coupon.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CouponIssueUseCase {

    private final CouponIssueTransactionService couponIssueTransactionService;
    private final CouponIssueDuplicateReadService couponIssueDuplicateReadService;
    private final CouponIssuanceHasher couponIssuanceHasher;

    public CouponIssueUseCase(
        CouponIssueTransactionService couponIssueTransactionService,
        CouponIssueDuplicateReadService couponIssueDuplicateReadService,
        CouponIssuanceHasher couponIssuanceHasher
    ) {
        this.couponIssueTransactionService = couponIssueTransactionService;
        this.couponIssueDuplicateReadService = couponIssueDuplicateReadService;
        this.couponIssuanceHasher = couponIssuanceHasher;
    }

    public CouponIssueResult issue(
        Long userId,
        Long couponPolicyId,
        CouponIssueCommand command
    ) {
        try {
            return couponIssueTransactionService.issue(userId, couponPolicyId, command);
        } catch (DataIntegrityViolationException exception) {
            return couponIssueDuplicateReadService.find(identityHash(userId, couponPolicyId, command))
                .orElseThrow(() -> exception);
        }
    }

    private String identityHash(Long userId, Long couponPolicyId, CouponIssueCommand command) {
        return switch (command.issueSourceType()) {
            case VISIT -> couponIssuanceHasher.hashVisitIssue(couponPolicyId, userId);
            case STAMPBOOK_COMPLETION -> couponIssuanceHasher.hashStampbookCompletionIssue(
                couponPolicyId,
                command.sourceId()
            );
            case MISSION_REWARD -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        };
    }

    public record CouponIssueCommand(
        CouponIssuanceType issueSourceType,
        Long sourceId
    ) {
    }
}
