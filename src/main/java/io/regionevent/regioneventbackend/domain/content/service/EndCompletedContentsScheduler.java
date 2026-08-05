package io.regionevent.regioneventbackend.domain.content.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EndCompletedContentsScheduler {

    private static final Logger log = LoggerFactory.getLogger(EndCompletedContentsScheduler.class);

    private final EndContentReservationsUseCase endContentReservationsUseCase;

    public EndCompletedContentsScheduler(
        EndContentReservationsUseCase endContentReservationsUseCase
    ) {
        this.endContentReservationsUseCase = endContentReservationsUseCase;
    }

    @Scheduled(
        initialDelayString = "${content.auto-ending.initial-delay:PT1M}",
        fixedDelayString = "${content.auto-ending.fixed-delay:PT1M}"
    )
    public void endCompletedContents() {
        UUID requestId = UUID.randomUUID();
        EndingCounts counts = new EndingCounts();

        for (Long contentId : endContentReservationsUseCase.findAutoEndCandidateIds()) {
            try {
                counts.record(endContentReservationsUseCase.endBySystem(contentId, requestId));
            } catch (RuntimeException exception) {
                counts.failedContentCount++;
                log.error(
                    "Completed content ending failed. requestId={}, contentId={}",
                    requestId,
                    contentId,
                    exception
                );
            }
        }

        log.info(
            "Completed content ending scheduler finished. requestId={}, endedContentCount={}, "
                + "skippedContentCount={}, failedContentCount={}, totalEndingDelayMillis={}",
            requestId,
            counts.endedContentCount,
            counts.skippedContentCount,
            counts.failedContentCount,
            counts.totalEndingDelayMillis
        );
    }

    private static class EndingCounts {

        private int endedContentCount;
        private int skippedContentCount;
        private int failedContentCount;
        private long totalEndingDelayMillis;

        private void record(EndContentReservationsSystemResult result) {
            switch (result.status()) {
                case ENDED -> {
                    endedContentCount++;
                    totalEndingDelayMillis += result.endingDelayMillis();
                }
                case SKIPPED -> skippedContentCount++;
            }
        }
    }
}
