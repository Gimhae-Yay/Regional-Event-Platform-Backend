package io.regionevent.regioneventbackend.domain.content.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ApprovedContentPublicationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ApprovedContentPublicationScheduler.class);

    private final PublishApprovedContentsUseCase publishApprovedContentsUseCase;
    private final Counter candidateContentCounter;
    private final Counter publishedContentCounter;
    private final Counter skippedContentCounter;
    private final Counter failedContentCounter;
    private final Timer publicationDelayTimer;

    public ApprovedContentPublicationScheduler(
        PublishApprovedContentsUseCase publishApprovedContentsUseCase,
        MeterRegistry meterRegistry
    ) {
        this.publishApprovedContentsUseCase = publishApprovedContentsUseCase;
        candidateContentCounter = meterRegistry.counter("content.publication.candidate");
        publishedContentCounter = meterRegistry.counter("content.publication.published");
        skippedContentCounter = meterRegistry.counter("content.publication.skipped");
        failedContentCounter = meterRegistry.counter("content.publication.failed");
        publicationDelayTimer = meterRegistry.timer("content.publication.delay");
    }

    @Scheduled(
        initialDelayString = "${content.publication.initial-delay:PT1M}",
        fixedDelayString = "${content.publication.fixed-delay:PT1M}"
    )
    public void publishApprovedContents() {
        PublishApprovedContentsResult result = publishApprovedContentsUseCase.publishApprovedContents();
        recordMetrics(result);
        log.atInfo()
            .addKeyValue("requestId", result.requestId())
            .addKeyValue("candidateContentCount", result.candidateContentCount())
            .addKeyValue("publishedContentCount", result.publishedContentCount())
            .addKeyValue("skippedContentCount", result.skippedContentCount())
            .addKeyValue("failedContentCount", result.failedContentCount())
            .addKeyValue("maximumPublicationDelayMillis", result.maximumPublicationDelay().toMillis())
            .log("Approved content publication scheduler finished");
    }

    private void recordMetrics(PublishApprovedContentsResult result) {
        candidateContentCounter.increment(result.candidateContentCount());
        publishedContentCounter.increment(result.publishedContentCount());
        skippedContentCounter.increment(result.skippedContentCount());
        failedContentCounter.increment(result.failedContentCount());
        result.publicationDelays().forEach(publicationDelayTimer::record);
    }
}
