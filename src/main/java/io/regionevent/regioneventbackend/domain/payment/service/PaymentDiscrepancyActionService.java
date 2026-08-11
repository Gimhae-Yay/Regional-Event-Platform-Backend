package io.regionevent.regioneventbackend.domain.payment.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
