package io.regionevent.regioneventbackend.domain.reservation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NoShowAndSessionCompletionScheduler {

    private static final Logger log = LoggerFactory.getLogger(NoShowAndSessionCompletionScheduler.class);

    private final ExpireNoShowsAndCompleteSessionUseCase expireNoShowsAndCompleteSessionUseCase;

    public NoShowAndSessionCompletionScheduler(
        ExpireNoShowsAndCompleteSessionUseCase expireNoShowsAndCompleteSessionUseCase
    ) {
        this.expireNoShowsAndCompleteSessionUseCase = expireNoShowsAndCompleteSessionUseCase;
    }

    @Scheduled(
        initialDelayString = "${reservation.no-show-completion.initial-delay:PT1M}",
        fixedDelayString = "${reservation.no-show-completion.fixed-delay:PT1M}"
    )
    public void expireNoShowsAndCompleteSessions() {
        NoShowAndSessionCompletionResult result = expireNoShowsAndCompleteSessionUseCase.execute();
        log.info(
            "No-show and session completion scheduler finished. requestId={}, expiredReservationCount={}, "
                + "completedSessionCount={}, failedSessionCount={}",
            result.requestId(),
            result.expiredReservationCount(),
            result.completedSessionCount(),
            result.failedSessionCount()
        );
    }
}
