package io.regionevent.regioneventbackend.domain.payment.service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.repository.RefundRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

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

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Refund> findByPaymentIdForUpdate(Long paymentId) {
        return refundRepository.findByPaymentIdForUpdate(paymentId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Refund> findByRefundIdForUpdate(Long refundId) {
        return refundRepository.findByRefundIdForUpdate(refundId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Refund create(Refund refund) {
        return refundRepository.saveAndFlush(refund);
    }

    public List<Refund> findAllOwnedByUserId(Long userId) {
        return refundRepository.findAllByPaymentOwnerUserIdOrderByRequestedAtDescRefundIdDesc(userId);
    }

    public Refund findOwnedByRefundId(Long userId, Long refundId) {
        return refundRepository.findByRefundIdAndPaymentOwnerUserId(refundId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<Refund> findAllByStatuses(Collection<RefundStatus> statuses) {
        return refundRepository.findAllByStatusIn(statuses);
    }
}
