package io.regionevent.regioneventbackend.domain.content.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ApprovedContentPublicationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ApprovedContentPublicationScheduler.class);

    private final PublishApprovedContentsUseCase publishApprovedContentsUseCase;

    public ApprovedContentPublicationScheduler(
        PublishApprovedContentsUseCase publishApprovedContentsUseCase
    ) {
        this.publishApprovedContentsUseCase = publishApprovedContentsUseCase;
    }

    @Scheduled(
        initialDelayString = "${content.publication.initial-delay:PT1M}",
        fixedDelayString = "${content.publication.fixed-delay:PT1M}"
    )
    public void publishApprovedContents() {
        PublishApprovedContentsResult result = publishApprovedContentsUseCase.publishApprovedContents();
        log.info(
            "Approved content publication scheduler finished. requestId={}, publishedContentCount={}, "
                + "skippedContentCount={}, failedContentCount={}",
            result.requestId(),
            result.publishedContentCount(),
            result.skippedContentCount(),
            result.failedContentCount()
        );
    }
}
