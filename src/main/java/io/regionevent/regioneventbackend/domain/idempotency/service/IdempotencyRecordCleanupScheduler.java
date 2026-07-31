package io.regionevent.regioneventbackend.domain.idempotency.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyRecordCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyRecordCleanupScheduler.class);
    private final IdempotencyRecordService idempotencyRecordService;

    public IdempotencyRecordCleanupScheduler(IdempotencyRecordService idempotencyRecordService) {
        this.idempotencyRecordService = idempotencyRecordService;
    }

    @Scheduled(cron = "${reservation.idempotency.cleanup-cron}")
    public void deleteExpiredTerminalRecords() {
        int deletedCount = idempotencyRecordService.deleteExpiredTerminalRecords();
        if (deletedCount > 0) {
            log.info("Expired idempotency records deleted. deletedCount={}", deletedCount);
        }
    }
}
