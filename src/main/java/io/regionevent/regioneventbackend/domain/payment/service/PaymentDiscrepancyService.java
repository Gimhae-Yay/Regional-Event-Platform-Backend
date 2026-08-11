package io.regionevent.regioneventbackend.domain.payment.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentDiscrepancyRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

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

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<PaymentDiscrepancy> findByPaymentIdForUpdate(Long paymentId) {
        return paymentDiscrepancyRepository.findByPaymentIdForUpdate(paymentId);
    }

    @Transactional(readOnly = true)
    public List<PaymentDiscrepancy> findAllByStatus(String status) {
        return paymentDiscrepancyRepository
            .findAllByStatusOrderByDetectedAtAscPaymentDiscrepancyIdAsc(status);
    }

    @Transactional(readOnly = true)
    public PaymentDiscrepancy findById(Long discrepancyId) {
        return paymentDiscrepancyRepository.findById(discrepancyId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
