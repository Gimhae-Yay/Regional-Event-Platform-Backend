package io.regionevent.regioneventbackend.domain.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.payment.entity.PaymentVerification;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentVerificationRepository;

@Service
public class PaymentVerificationService {

    private final PaymentVerificationRepository paymentVerificationRepository;

    public PaymentVerificationService(PaymentVerificationRepository paymentVerificationRepository) {
        this.paymentVerificationRepository = paymentVerificationRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentVerification create(PaymentVerification verification) {
        return paymentVerificationRepository.saveAndFlush(verification);
    }
}
