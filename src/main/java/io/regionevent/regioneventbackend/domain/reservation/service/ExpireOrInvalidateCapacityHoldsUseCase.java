package io.regionevent.regioneventbackend.domain.reservation.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;
import io.regionevent.regioneventbackend.domain.payment.service.ExpirePendingPaymentForTerminatedHoldUseCase;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;

@Service
public class ExpireOrInvalidateCapacityHoldsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExpireOrInvalidateCapacityHoldsUseCase.class);
    private static final String SESSION_STARTED_INVALIDATION_REASON = "SESSION_STARTED";

    private final CapacityHoldService capacityHoldService;
    private final ContentSessionService contentSessionService;
    private final ExpirePendingPaymentForTerminatedHoldUseCase expirePendingPaymentForTerminatedHoldUseCase;
    private final TransactionTemplate holdTransactionTemplate;

    public ExpireOrInvalidateCapacityHoldsUseCase(
        CapacityHoldService capacityHoldService,
        ContentSessionService contentSessionService,
        ExpirePendingPaymentForTerminatedHoldUseCase expirePendingPaymentForTerminatedHoldUseCase,
        PlatformTransactionManager transactionManager
    ) {
        this.capacityHoldService = capacityHoldService;
        this.contentSessionService = contentSessionService;
        this.expirePendingPaymentForTerminatedHoldUseCase = expirePendingPaymentForTerminatedHoldUseCase;
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
                    .map(capacityHold -> {
                        expirePendingPaymentForTerminatedHoldUseCase.expire(capacityHold, requestId, null);
                        return toProcessingResult(capacityHold.nextStatus());
                    })
                    .orElse(HoldTerminationProcessingResult.SKIPPED)
            );
            counts.record(result);
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
                () -> capacityHoldService.expireOrInvalidateExpiredHoldIfActive(
                    holdId,
                    SESSION_STARTED_INVALIDATION_REASON
                )
                    .map(capacityHold -> {
                        expirePendingPaymentForTerminatedHoldUseCase.expire(capacityHold, requestId, null);
                        return toProcessingResult(capacityHold.nextStatus());
                    })
                    .orElse(HoldTerminationProcessingResult.SKIPPED)
            );
            counts.record(result);
        });
    }

    private HoldTerminationProcessingResult processHold(
        Long holdId,
        UUID requestId,
        HoldTerminationOperation operation
    ) {
        try {
            HoldTerminationProcessingResult result = holdTransactionTemplate.execute(
                status -> {
                    if (!lockContentSessionBeforeHold(holdId)) {
                        return HoldTerminationProcessingResult.SKIPPED;
                    }
                    return operation.execute();
                }
            );
            return result == null
                ? HoldTerminationProcessingResult.SKIPPED
                : result;
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

    private boolean lockContentSessionBeforeHold(Long holdId) {
        return capacityHoldService.findContentSessionId(holdId)
            .map(sessionId -> {
                contentSessionService.lockForUpdate(sessionId);
                return true;
            })
            .orElse(false);
    }

    private HoldTerminationProcessingResult toProcessingResult(CapacityHoldStatus capacityHoldStatus) {
        return switch (capacityHoldStatus) {
            case EXPIRED -> HoldTerminationProcessingResult.EXPIRED;
            case INVALIDATED -> HoldTerminationProcessingResult.INVALIDATED;
            default -> throw new IllegalStateException(
                "expired capacity hold must be terminated"
            );
        };
    }

    @FunctionalInterface
    private interface HoldTerminationOperation {

        HoldTerminationProcessingResult execute();
    }

    private enum HoldTerminationProcessingResult {
        EXPIRED,
        INVALIDATED,
        SKIPPED,
        FAILED
    }

    private static class HoldTerminationCounts {

        private int expiredHoldCount;
        private int invalidatedHoldCount;
        private int failedHoldCount;

        private void record(HoldTerminationProcessingResult result) {
            switch (result) {
                case EXPIRED -> expiredHoldCount++;
                case INVALIDATED -> invalidatedHoldCount++;
                case FAILED -> failedHoldCount++;
                case SKIPPED -> {
                }
            }
        }
    }
}
