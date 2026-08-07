package io.regionevent.regioneventbackend.domain.idempotency.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyCleanupScheduler {

    private final IdempotencyService idempotencyService;

    public IdempotencyCleanupScheduler(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Scheduled(
        initialDelayString = "${idempotency.cleanup-initial-delay:PT1H}",
        fixedDelayString = "${idempotency.cleanup-fixed-delay:PT1H}"
    )
    public void deleteExpiredTerminalRecords() {
        idempotencyService.deleteExpiredTerminalRecords();
    }
}
