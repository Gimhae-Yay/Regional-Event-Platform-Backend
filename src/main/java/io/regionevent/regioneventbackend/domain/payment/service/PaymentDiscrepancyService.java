package io.regionevent.regioneventbackend.domain.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancyAction;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentDiscrepancyRepository;

@Service
public class PaymentDiscrepancyService {

    private final PaymentDiscrepancyRepository paymentDiscrepancyRepository;
    private final io.regionevent.regioneventbackend.domain.payment.repository.PaymentDiscrepancyActionRepository paymentDiscrepancyActionRepository;

    public PaymentDiscrepancyService(
        PaymentDiscrepancyRepository paymentDiscrepancyRepository,
        io.regionevent.regioneventbackend.domain.payment.repository.PaymentDiscrepancyActionRepository paymentDiscrepancyActionRepository
    ) {
        this.paymentDiscrepancyRepository = paymentDiscrepancyRepository;
        this.paymentDiscrepancyActionRepository = paymentDiscrepancyActionRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentDiscrepancy create(PaymentDiscrepancy discrepancy) {
        return paymentDiscrepancyRepository.saveAndFlush(discrepancy);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public java.util.Optional<PaymentDiscrepancy> findByPaymentIdForUpdate(Long paymentId) {
        return paymentDiscrepancyRepository.findByPaymentIdForUpdate(paymentId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentDiscrepancyAction createAction(
        PaymentDiscrepancy discrepancy,
        String actionType,
        String evidenceReference,
        String reasonCode,
        String resultCode,
        java.time.Instant actedAt
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
