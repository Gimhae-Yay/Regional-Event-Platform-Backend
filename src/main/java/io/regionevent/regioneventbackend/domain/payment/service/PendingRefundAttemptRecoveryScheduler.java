package io.regionevent.regioneventbackend.domain.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PendingRefundAttemptRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingRefundAttemptRecoveryScheduler.class);

    private final RecoverPendingRefundAttemptsUseCase recoverPendingRefundAttemptsUseCase;

    public PendingRefundAttemptRecoveryScheduler(
        RecoverPendingRefundAttemptsUseCase recoverPendingRefundAttemptsUseCase
    ) {
        this.recoverPendingRefundAttemptsUseCase = recoverPendingRefundAttemptsUseCase;
    }

    @Scheduled(
        initialDelayString = "${payment.refund-recovery.initial-delay:PT1M}",
        fixedDelayString = "${payment.refund-recovery.fixed-delay:PT1M}"
    )
    public void recoverPendingRefundAttempts() {
        RecoverPendingRefundAttemptsUseCase.RecoveryResult result = recoverPendingRefundAttemptsUseCase.recover();
        log.info(
            "Pending refund attempt recovery finished. candidateCount={}, recoveredCount={}, discrepantCount={}",
            result.candidateCount(),
            result.recoveredCount(),
            result.discrepantCount()
        );
    }
}
