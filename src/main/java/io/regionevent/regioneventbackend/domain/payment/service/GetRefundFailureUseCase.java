package io.regionevent.regioneventbackend.domain.payment.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;

@Service
public class GetRefundFailureUseCase {

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final RefundService refundService;
    private final RefundAttemptService refundAttemptService;

    public GetRefundFailureUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        RefundService refundService,
        RefundAttemptService refundAttemptService
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.refundService = refundService;
        this.refundAttemptService = refundAttemptService;
    }

    @Transactional(readOnly = true)
    public RefundFailureDetailInfo get(Long actorUserId, Long refundId) {
        platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(actorUserId);
        Refund refund = refundService.findByRefundId(refundId);
        List<RefundAttempt> attempts = refundAttemptService.findAllByRefundId(refundId);
        return RefundFailureDetailInfo.from(refund, attempts);
    }
}
