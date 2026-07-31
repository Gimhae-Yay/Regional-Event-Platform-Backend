package io.regionevent.regioneventbackend.domain.image.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.content.service.ContentRepresentativeImageReferenceService;
import io.regionevent.regioneventbackend.domain.content.service.ContentRevisionImageReferenceService;

@Service
public class CleanupExpiredImageUploadUseCase {

    private static final Logger log = LoggerFactory.getLogger(CleanupExpiredImageUploadUseCase.class);

    private final ImageObjectCleanupService imageObjectCleanupService;
    private final ContentRepresentativeImageReferenceService contentRepresentativeImageReferenceService;
    private final ContentRevisionImageReferenceService contentRevisionImageReferenceService;
    private final ImageStorageGateway imageStorageGateway;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public CleanupExpiredImageUploadUseCase(
        ImageObjectCleanupService imageObjectCleanupService,
        ContentRepresentativeImageReferenceService contentRepresentativeImageReferenceService,
        ContentRevisionImageReferenceService contentRevisionImageReferenceService,
        ImageStorageGateway imageStorageGateway,
        PlatformTransactionManager transactionManager,
        Clock clock
    ) {
        this.imageObjectCleanupService = imageObjectCleanupService;
        this.contentRepresentativeImageReferenceService = contentRepresentativeImageReferenceService;
        this.contentRevisionImageReferenceService = contentRevisionImageReferenceService;
        this.imageStorageGateway = imageStorageGateway;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public void cleanupExpiredUploadCandidates() {
        Instant now = clock.instant();
        List<Long> candidateIds = imageObjectCleanupService.findUploadCleanupCandidateIds(now);
        candidateIds.forEach(candidateId -> cleanupCandidate(candidateId, now));
    }

    private void cleanupCandidate(Long imageObjectId, Instant now) {
        String objectKey = transitionToDeletePending(imageObjectId, now);
        if (objectKey == null) {
            return;
        }

        try {
            imageStorageGateway.deleteObject(objectKey);
            transactionTemplate.executeWithoutResult(status -> imageObjectCleanupService.deleteById(imageObjectId));
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> imageObjectCleanupService.recordDeleteAttempt(
                imageObjectId,
                now
            ));
            log.warn("Failed to delete expired image object. imageObjectId={}", imageObjectId);
        }
    }

    private String transitionToDeletePending(Long imageObjectId, Instant now) {
        return transactionTemplate.execute(status -> imageObjectCleanupService
            .findCleanupCandidateForUpdate(imageObjectId, now)
            .filter(imageObject -> hasNoDirectReference(imageObject.getImageObjectId()))
            .map(imageObject -> {
                imageObject.markDeletePending();
                return imageObject.getObjectKey();
            })
            .orElse(null));
    }

    private boolean hasNoDirectReference(Long imageObjectId) {
        return !contentRepresentativeImageReferenceService.hasRepresentativeImageReference(imageObjectId)
            && !contentRevisionImageReferenceService.hasCandidateImageReference(imageObjectId);
    }
}
