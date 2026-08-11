package io.regionevent.regioneventbackend.domain.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentDiscrepancyRepository;

@Service
public class PaymentDiscrepancyService {

    private final PaymentDiscrepancyRepository paymentDiscrepancyRepository;

    public PaymentDiscrepancyService(PaymentDiscrepancyRepository paymentDiscrepancyRepository) {
        this.paymentDiscrepancyRepository = paymentDiscrepancyRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentDiscrepancy create(PaymentDiscrepancy discrepancy) {
        return paymentDiscrepancyRepository.saveAndFlush(discrepancy);
    }
}
