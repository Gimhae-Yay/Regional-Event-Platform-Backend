package io.regionevent.regioneventbackend.domain.image.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;

@Service
public class ImageObjectCleanupService {

    private static final int CLEANUP_BATCH_SIZE = 100;

    private final ImageObjectRepository imageObjectRepository;
    private final ImageStorageGateway imageStorageGateway;
    private final TransactionTemplate transactionTemplate;

    public ImageObjectCleanupService(
        ImageObjectRepository imageObjectRepository,
        ImageStorageGateway imageStorageGateway,
        PlatformTransactionManager transactionManager
    ) {
        this.imageObjectRepository = imageObjectRepository;
        this.imageStorageGateway = imageStorageGateway;
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public int cleanupExpiredUnlinkedUploadCandidates() {
        List<ImageObjectCleanupTarget> cleanupTargets = transactionTemplate.execute(status -> findCleanupTargets());
        if (cleanupTargets == null) {
            return 0;
        }
        return deleteStorageObjects(cleanupTargets);
    }

    private List<ImageObjectCleanupTarget> findCleanupTargets() {
        List<ImageObject> expiredUploadCandidates =
            imageObjectRepository.findExpiredUnlinkedUploadCandidateIdsWithoutDirectReferences(
                ImageLifecycleStatus.ACTIVE,
                PageRequest.of(0, CLEANUP_BATCH_SIZE)
            ).stream()
                .map(this::markDeletePendingIfStillUnlinked)
                .flatMap(Optional::stream)
                .toList();

        List<ImageObject> retryableDeletePendingObjects =
            imageObjectRepository.findRetryableDeletePendingObjectsWithoutDirectReferences(
                ImageLifecycleStatus.DELETE_PENDING,
                PageRequest.of(0, CLEANUP_BATCH_SIZE)
            );

        return toCleanupTargets(expiredUploadCandidates, retryableDeletePendingObjects);
    }

    private Optional<ImageObject> markDeletePendingIfStillUnlinked(Long imageObjectId) {
        int updatedCount = imageObjectRepository.markExpiredUnlinkedUploadCandidateDeletePending(
            imageObjectId,
            ImageLifecycleStatus.ACTIVE,
            ImageLifecycleStatus.DELETE_PENDING
        );
        if (updatedCount == 0) {
            return Optional.empty();
        }
        return imageObjectRepository.findById(imageObjectId);
    }

    private List<ImageObjectCleanupTarget> toCleanupTargets(
        List<ImageObject> expiredUploadCandidates,
        List<ImageObject> retryableDeletePendingObjects
    ) {
        Map<Long, ImageObjectCleanupTarget> cleanupTargets = new LinkedHashMap<>();
        expiredUploadCandidates.stream()
            .map(ImageObjectCleanupTarget::from)
            .forEach(cleanupTarget -> cleanupTargets.put(cleanupTarget.imageObjectId(), cleanupTarget));
        retryableDeletePendingObjects.stream()
            .map(ImageObjectCleanupTarget::from)
            .forEach(cleanupTarget -> cleanupTargets.putIfAbsent(cleanupTarget.imageObjectId(), cleanupTarget));
        return List.copyOf(cleanupTargets.values());
    }

    private int deleteStorageObjects(List<ImageObjectCleanupTarget> cleanupTargets) {
        int deletedCount = 0;
        for (ImageObjectCleanupTarget cleanupTarget : cleanupTargets) {
            if (deleteStorageObject(cleanupTarget)) {
                deletedCount += deleteImageObject(cleanupTarget.imageObjectId());
            } else {
                recordDeleteAttempt(cleanupTarget.imageObjectId());
            }
        }
        return deletedCount;
    }

    private boolean deleteStorageObject(ImageObjectCleanupTarget cleanupTarget) {
        try {
            imageStorageGateway.delete(cleanupTarget.objectKey());
            return true;
        } catch (ImageStorageException exception) {
            return false;
        }
    }

    private int deleteImageObject(Long imageObjectId) {
        Integer deletedCount = transactionTemplate.execute(status ->
            imageObjectRepository.deleteDeletePendingObjectWithoutDirectReferences(
                imageObjectId,
                ImageLifecycleStatus.DELETE_PENDING
            )
        );
        if (deletedCount == null) {
            return 0;
        }
        return deletedCount;
    }

    private void recordDeleteAttempt(Long imageObjectId) {
        transactionTemplate.executeWithoutResult(status -> {
            Instant attemptedAt = imageObjectRepository.findCurrentTimestamp();
            imageObjectRepository.findById(imageObjectId)
                .filter(imageObject -> imageObject.getLifecycleStatus() == ImageLifecycleStatus.DELETE_PENDING)
                .ifPresent(imageObject -> imageObject.recordDeleteAttempt(attemptedAt));
        });
    }

    private record ImageObjectCleanupTarget(
        Long imageObjectId,
        String objectKey
    ) {

        static ImageObjectCleanupTarget from(ImageObject imageObject) {
            return new ImageObjectCleanupTarget(
                imageObject.getImageObjectId(),
                imageObject.getObjectKey()
            );
        }
    }
}
