package io.regionevent.regioneventbackend.domain.payment.service;

import java.util.EnumSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.repository.RefundRepository;

@Service
public class RefundService {

    private static final EnumSet<RefundStatus> IN_PROGRESS_STATUSES = EnumSet.of(
        RefundStatus.REQUESTED,
        RefundStatus.PROCESSING
    );

    private final RefundRepository refundRepository;

    public RefundService(RefundRepository refundRepository) {
        this.refundRepository = refundRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public boolean hasInProgressRefund(Long userId) {
        return refundRepository.existsByPaymentCapacityHoldUserUserIdAndStatusIn(
            userId,
            IN_PROGRESS_STATUSES
        );
    }

    public List<Refund> findAllOwnedByUserId(Long userId) {
        return refundRepository.findAllByPaymentOwnerUserIdOrderByRequestedAtDescRefundIdDesc(userId);
    }
}
