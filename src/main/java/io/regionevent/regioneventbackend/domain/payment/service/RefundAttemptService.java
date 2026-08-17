package io.regionevent.regioneventbackend.domain.payment.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptOutcomeKind;
import io.regionevent.regioneventbackend.domain.payment.repository.RefundAttemptRepository;

@Service
public class RefundAttemptService {

    private final RefundAttemptRepository refundAttemptRepository;

    public RefundAttemptService(RefundAttemptRepository refundAttemptRepository) {
        this.refundAttemptRepository = refundAttemptRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public RefundAttempt create(RefundAttempt refundAttempt) {
        return refundAttemptRepository.saveAndFlush(refundAttempt);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RefundAttempt> findByRefundAttemptIdForUpdate(Long refundAttemptId) {
        return refundAttemptRepository.findByRefundAttemptIdForUpdate(refundAttemptId);
    }

    @Transactional
    public List<RecoveryCandidate> findRecoveryCandidates(java.time.Instant latestAttemptedAt) {
        return refundAttemptRepository.findRecoveryCandidatesForUpdate(
            RefundAttemptOutcomeKind.PENDING,
            latestAttemptedAt
        ).stream().map(attempt -> new RecoveryCandidate(
            attempt.getRefundAttemptId(),
            attempt.getRefund().getRefundId(),
            attempt.getRefund().getPayment().getPortonePaymentId()
        )).toList();
    }

    @Transactional(readOnly = true)
    public List<RefundAttempt> findAllByRefundIds(List<Long> refundIds) {
        if (refundIds.isEmpty()) {
            return List.of();
        }
        return refundAttemptRepository.findAllByRefundRefundIdIn(refundIds);
    }

    @Transactional(readOnly = true)
    public List<RefundAttempt> findAllByRefundId(Long refundId) {
        return refundAttemptRepository.findAllByRefundRefundIdOrderByAttemptNoAsc(refundId);
    }

    public record RecoveryCandidate(
        Long refundAttemptId,
        Long refundId,
        String portonePaymentId
    ) {
    }
}
