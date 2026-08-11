package io.regionevent.regioneventbackend.domain.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
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
}
