package io.regionevent.regioneventbackend.domain.content.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PublishApprovedContentsUseCase {

    private static final Logger log = LoggerFactory.getLogger(PublishApprovedContentsUseCase.class);

    private final ContentService contentService;
    private final PublishApprovedContentUseCase publishApprovedContentUseCase;

    public PublishApprovedContentsUseCase(
        ContentService contentService,
        PublishApprovedContentUseCase publishApprovedContentUseCase
    ) {
        this.contentService = contentService;
        this.publishApprovedContentUseCase = publishApprovedContentUseCase;
    }

    public PublishApprovedContentsResult publishApprovedContents() {
        UUID requestId = UUID.randomUUID();
        PublicationCounts counts = new PublicationCounts();

        for (Long contentId : contentService.findApprovedPublicationCandidateIds()) {
            try {
                counts.record(publishApprovedContentUseCase.publish(contentId, requestId));
            } catch (RuntimeException exception) {
                counts.failedContentCount++;
                log.error(
                    "Approved content publication failed. requestId={}, contentId={}",
                    requestId,
                    contentId,
                    exception
                );
            }
        }

        return new PublishApprovedContentsResult(
            requestId,
            counts.publishedContentCount,
            counts.skippedContentCount,
            counts.failedContentCount
        );
    }

    private static class PublicationCounts {

        private int publishedContentCount;
        private int skippedContentCount;
        private int failedContentCount;

        private void record(PublishApprovedContentResult result) {
            switch (result) {
                case PUBLISHED -> publishedContentCount++;
                case SKIPPED -> skippedContentCount++;
            }
        }
    }
}
