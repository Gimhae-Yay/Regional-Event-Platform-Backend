package io.regionevent.regioneventbackend.domain.payment.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancyAction;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentDiscrepancyActionRepository;

@Service
public class PaymentDiscrepancyActionService {

    private final PaymentDiscrepancyActionRepository paymentDiscrepancyActionRepository;

    public PaymentDiscrepancyActionService(
        PaymentDiscrepancyActionRepository paymentDiscrepancyActionRepository
    ) {
        this.paymentDiscrepancyActionRepository = paymentDiscrepancyActionRepository;
    }

    @Transactional(readOnly = true)
    public List<PaymentDiscrepancyAction> findAllByDiscrepancyId(Long discrepancyId) {
        return paymentDiscrepancyActionRepository
            .findAllByPaymentDiscrepancyPaymentDiscrepancyIdOrderByActedAtAscPaymentDiscrepancyActionIdAsc(
                discrepancyId
            );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentDiscrepancyAction create(
        PaymentDiscrepancy discrepancy,
        String actionType,
        String evidenceReference,
        String reasonCode,
        String resultCode,
        Instant actedAt
    ) {
        return paymentDiscrepancyActionRepository.saveAndFlush(new PaymentDiscrepancyAction(
            discrepancy,
            actionType,
            evidenceReference,
            reasonCode,
            resultCode,
            actedAt
        ));
    }
}
