package io.regionevent.regioneventbackend.domain.idempotency.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecordStatus;
import io.regionevent.regioneventbackend.domain.idempotency.repository.IdempotencyRecordRepository;

@Component
public class IdempotencyRecordCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyRecordCleanupScheduler.class);
    private static final List<IdempotencyRecordStatus> TERMINAL_STATUSES = List.of(
        IdempotencyRecordStatus.SUCCEEDED,
        IdempotencyRecordStatus.FAILED
    );

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public IdempotencyRecordCleanupScheduler(IdempotencyRecordRepository idempotencyRecordRepository) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Scheduled(cron = "${reservation.idempotency.cleanup-cron}")
    @Transactional
    public void deleteExpiredTerminalRecords() {
        int deletedCount = idempotencyRecordRepository.deleteExpiredTerminalRecords(TERMINAL_STATUSES);
        if (deletedCount > 0) {
            log.info("Expired idempotency records deleted. deletedCount={}", deletedCount);
        }
    }
}
