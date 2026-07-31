package io.regionevent.regioneventbackend.domain.image.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;

@Service
public class ImageObjectCleanupService {

    private final ImageObjectRepository imageObjectRepository;

    public ImageObjectCleanupService(ImageObjectRepository imageObjectRepository) {
        this.imageObjectRepository = imageObjectRepository;
    }

    @Transactional(readOnly = true)
    public List<Long> findUploadCleanupCandidateIds(Instant now) {
        return imageObjectRepository.findUploadCleanupCandidateIds(
            ImageLifecycleStatus.ACTIVE,
            ImageLifecycleStatus.DELETE_PENDING,
            now
        );
    }

    public Optional<ImageObject> findCleanupCandidateForUpdate(Long imageObjectId, Instant now) {
        return imageObjectRepository.findByIdForUpdate(imageObjectId)
            .filter(imageObject -> isCleanupCandidate(imageObject, now));
    }

    public void deleteById(Long imageObjectId) {
        imageObjectRepository.deleteById(imageObjectId);
    }

    public void recordDeleteAttempt(Long imageObjectId, Instant now) {
        imageObjectRepository.findByIdForUpdate(imageObjectId)
            .ifPresent(imageObject -> imageObject.recordDeleteAttempt(now));
    }

    private static boolean isCleanupCandidate(ImageObject imageObject, Instant now) {
        if (imageObject.getLifecycleStatus() == ImageLifecycleStatus.DELETE_PENDING) {
            return true;
        }
        return imageObject.getLifecycleStatus() == ImageLifecycleStatus.ACTIVE
            && imageObject.getLinkedAt() == null
            && imageObject.getUploadExpiresAt() != null
            && !imageObject.getUploadExpiresAt().isAfter(now);
    }
}
