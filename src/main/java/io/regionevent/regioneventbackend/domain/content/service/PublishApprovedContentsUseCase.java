package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

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
        List<Long> candidateContentIds = contentService.findApprovedPublicationCandidateIds();

        for (Long contentId : candidateContentIds) {
            try {
                counts.record(publishApprovedContentUseCase.publish(contentId, requestId));
            } catch (RuntimeException exception) {
                counts.failedContentCount++;
                log.atError()
                    .addKeyValue("requestId", requestId)
                    .addKeyValue("contentId", contentId)
                    .addKeyValue("failureCode", resolveFailureCode(exception))
                    .setCause(exception)
                    .log("Approved content publication candidate failed");
            }
        }

        return new PublishApprovedContentsResult(
            requestId,
            candidateContentIds.size(),
            counts.publishedContentCount,
            counts.skippedContentCount,
            counts.failedContentCount,
            counts.publicationDelays
        );
    }

    private String resolveFailureCode(RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().code();
        }
        return ErrorCode.INTERNAL_SERVER_ERROR.code();
    }

    private static class PublicationCounts {

        private int publishedContentCount;
        private int skippedContentCount;
        private int failedContentCount;
        private final List<Duration> publicationDelays = new ArrayList<>();

        private void record(PublishApprovedContentResult result) {
            switch (result.status()) {
                case PUBLISHED -> {
                    publishedContentCount++;
                    publicationDelays.add(result.publicationDelay());
                }
                case SKIPPED -> skippedContentCount++;
            }
        }
    }
}
