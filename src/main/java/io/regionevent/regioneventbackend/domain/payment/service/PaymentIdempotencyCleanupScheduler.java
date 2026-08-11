package io.regionevent.regioneventbackend.domain.payment.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentIdempotencyCleanupScheduler {

    private final PaymentIdempotencyService paymentIdempotencyService;

    public PaymentIdempotencyCleanupScheduler(PaymentIdempotencyService paymentIdempotencyService) {
        this.paymentIdempotencyService = paymentIdempotencyService;
    }

    @Scheduled(
        initialDelayString = "${payment.idempotency.cleanup-initial-delay:PT1H}",
        fixedDelayString = "${payment.idempotency.cleanup-fixed-delay:PT1H}"
    )
    public void deleteExpiredTerminalRecords() {
        paymentIdempotencyService.deleteExpiredTerminalRecords();
    }
}
