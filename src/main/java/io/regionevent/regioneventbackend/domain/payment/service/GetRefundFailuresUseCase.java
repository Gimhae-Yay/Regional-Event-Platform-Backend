package io.regionevent.regioneventbackend.domain.payment.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;

@Service
public class GetRefundFailuresUseCase {

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final RefundService refundService;
    private final RefundAttemptService refundAttemptService;

    public GetRefundFailuresUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        RefundService refundService,
        RefundAttemptService refundAttemptService
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.refundService = refundService;
        this.refundAttemptService = refundAttemptService;
    }

    @Transactional(readOnly = true)
    public List<RefundFailureListInfo> get(
        Long actorUserId,
        Collection<RefundStatus> statuses
    ) {
        platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(actorUserId);
        List<Refund> refunds = refundService.findAllByStatuses(statuses);
        Map<Long, List<RefundAttempt>> attemptsByRefundId = refundAttemptService.findAllByRefundIds(
            refunds.stream().map(Refund::getRefundId).toList()
        ).stream().collect(Collectors.groupingBy(attempt -> attempt.getRefund().getRefundId()));

        return refunds.stream()
            .map(refund -> RefundFailureListInfo.from(
                refund,
                attemptsByRefundId.getOrDefault(refund.getRefundId(), List.of())
            ))
            .sorted(Comparator.comparing(RefundFailureListInfo::updatedAt)
                .thenComparing(RefundFailureListInfo::refundId))
            .toList();
    }
}
