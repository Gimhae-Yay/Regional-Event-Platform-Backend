package io.regionevent.regioneventbackend.domain.reservation.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ExpireOrInvalidateCapacityHoldsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExpireOrInvalidateCapacityHoldsUseCase.class);
    private static final String SESSION_STARTED_INVALIDATION_REASON = "SESSION_STARTED";

    private final CapacityHoldService capacityHoldService;
    private final TransactionTemplate holdTransactionTemplate;

    public ExpireOrInvalidateCapacityHoldsUseCase(
        CapacityHoldService capacityHoldService,
        PlatformTransactionManager transactionManager
    ) {
        this.capacityHoldService = capacityHoldService;
        holdTransactionTemplate = new TransactionTemplate(transactionManager);
        holdTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public HoldTerminationResult execute() {
        UUID requestId = UUID.randomUUID();
        HoldTerminationCounts counts = new HoldTerminationCounts();

        processStartedSessionHolds(requestId, counts);
        processExpiredHolds(requestId, counts);

        return new HoldTerminationResult(
            requestId,
            counts.expiredHoldCount,
            counts.invalidatedHoldCount,
            counts.failedHoldCount
        );
    }

    private void processStartedSessionHolds(
        UUID requestId,
        HoldTerminationCounts counts
    ) {
        capacityHoldService.findActiveHoldIdsForStartedSessions().forEach(holdId -> {
            HoldTerminationProcessingResult result = processHold(
                holdId,
                requestId,
                () -> capacityHoldService.invalidateAndReleaseCapacityIfActive(
                    holdId,
                    SESSION_STARTED_INVALIDATION_REASON
                )
            );
            counts.recordInvalidation(result);
        });
    }

    private void processExpiredHolds(
        UUID requestId,
        HoldTerminationCounts counts
    ) {
        capacityHoldService.findExpiredActiveHoldIds().forEach(holdId -> {
            HoldTerminationProcessingResult result = processHold(
                holdId,
                requestId,
                () -> capacityHoldService.expireAndReleaseCapacityIfActive(holdId)
            );
            counts.recordExpiration(result);
        });
    }

    private HoldTerminationProcessingResult processHold(
        Long holdId,
        UUID requestId,
        HoldTerminationOperation operation
    ) {
        try {
            Boolean terminated = holdTransactionTemplate.execute(status -> operation.execute());
            return Boolean.TRUE.equals(terminated)
                ? HoldTerminationProcessingResult.TERMINATED
                : HoldTerminationProcessingResult.SKIPPED;
        } catch (RuntimeException exception) {
            log.error(
                "Capacity hold termination failed. requestId={}, holdId={}",
                requestId,
                holdId,
                exception
            );
            return HoldTerminationProcessingResult.FAILED;
        }
    }

    @FunctionalInterface
    private interface HoldTerminationOperation {

        boolean execute();
    }

    private enum HoldTerminationProcessingResult {
        TERMINATED,
        SKIPPED,
        FAILED
    }

    private static class HoldTerminationCounts {

        private int expiredHoldCount;
        private int invalidatedHoldCount;
        private int failedHoldCount;

        private void recordExpiration(HoldTerminationProcessingResult result) {
            if (result == HoldTerminationProcessingResult.TERMINATED) {
                expiredHoldCount++;
            }
            if (result == HoldTerminationProcessingResult.FAILED) {
                failedHoldCount++;
            }
        }

        private void recordInvalidation(HoldTerminationProcessingResult result) {
            if (result == HoldTerminationProcessingResult.TERMINATED) {
                invalidatedHoldCount++;
            }
            if (result == HoldTerminationProcessingResult.FAILED) {
                failedHoldCount++;
            }
        }
    }
}
