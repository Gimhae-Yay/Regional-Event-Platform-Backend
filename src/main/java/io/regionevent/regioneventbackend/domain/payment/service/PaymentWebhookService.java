package io.regionevent.regioneventbackend.domain.payment.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.payment.entity.PaymentWebhook;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentWebhookRepository;

@Service
public class PaymentWebhookService {

    private final PaymentWebhookRepository paymentWebhookRepository;

    public PaymentWebhookService(PaymentWebhookRepository paymentWebhookRepository) {
        this.paymentWebhookRepository = paymentWebhookRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean existsByProviderEventId(String providerEventId) {
        return paymentWebhookRepository.findByProviderEventId(providerEventId).isPresent();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentWebhook create(PaymentWebhook paymentWebhook) {
        return paymentWebhookRepository.saveAndFlush(paymentWebhook);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean createIfAbsent(PaymentWebhook paymentWebhook) {
        return paymentWebhookRepository.insertIfAbsent(
            paymentWebhook.getProviderEventId(),
            paymentWebhook.getPayment() == null ? null : paymentWebhook.getPayment().getPaymentId(),
            paymentWebhook.getAuthenticationResult(),
            paymentWebhook.getProcessingResult(),
            paymentWebhook.getPayloadHash(),
            paymentWebhook.getReceivedAt()
        ) == 1;
    }
}
