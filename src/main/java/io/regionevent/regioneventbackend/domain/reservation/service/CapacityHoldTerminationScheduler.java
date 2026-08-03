package io.regionevent.regioneventbackend.domain.reservation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CapacityHoldTerminationScheduler {

    private static final Logger log = LoggerFactory.getLogger(CapacityHoldTerminationScheduler.class);

    private final ExpireOrInvalidateCapacityHoldsUseCase expireOrInvalidateCapacityHoldsUseCase;

    public CapacityHoldTerminationScheduler(
        ExpireOrInvalidateCapacityHoldsUseCase expireOrInvalidateCapacityHoldsUseCase
    ) {
        this.expireOrInvalidateCapacityHoldsUseCase = expireOrInvalidateCapacityHoldsUseCase;
    }

    @Scheduled(
        initialDelayString = "${reservation.hold-termination.initial-delay:PT1M}",
        fixedDelayString = "${reservation.hold-termination.fixed-delay:PT1M}"
    )
    public void expireOrInvalidateCapacityHolds() {
        HoldTerminationResult result = expireOrInvalidateCapacityHoldsUseCase.execute();
        log.info(
            "Capacity hold termination scheduler finished. requestId={}, expiredHoldCount={}, "
                + "invalidatedHoldCount={}, failedHoldCount={}",
            result.requestId(),
            result.expiredHoldCount(),
            result.invalidatedHoldCount(),
            result.failedHoldCount()
        );
    }
}
