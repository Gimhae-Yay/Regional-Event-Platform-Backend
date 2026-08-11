package io.regionevent.regioneventbackend.domain.payment.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancyAction;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentDiscrepancyActionRepository;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentDiscrepancyRepository;

@Service
public class PaymentDiscrepancyService {

    private final PaymentDiscrepancyRepository paymentDiscrepancyRepository;
    private final PaymentDiscrepancyActionRepository paymentDiscrepancyActionRepository;

    public PaymentDiscrepancyService(
        PaymentDiscrepancyRepository paymentDiscrepancyRepository,
        PaymentDiscrepancyActionRepository paymentDiscrepancyActionRepository
    ) {
        this.paymentDiscrepancyRepository = paymentDiscrepancyRepository;
        this.paymentDiscrepancyActionRepository = paymentDiscrepancyActionRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentDiscrepancy create(PaymentDiscrepancy discrepancy) {
        return paymentDiscrepancyRepository.saveAndFlush(discrepancy);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<PaymentDiscrepancy> findByPaymentIdForUpdate(Long paymentId) {
        return paymentDiscrepancyRepository.findByPaymentIdForUpdate(paymentId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentDiscrepancyAction createAction(
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

    @Transactional(readOnly = true)
    public List<PaymentDiscrepancy> findAllByStatus(String status) {
        return paymentDiscrepancyRepository
            .findAllByStatusOrderByDetectedAtAscPaymentDiscrepancyIdAsc(status);
    }
}
