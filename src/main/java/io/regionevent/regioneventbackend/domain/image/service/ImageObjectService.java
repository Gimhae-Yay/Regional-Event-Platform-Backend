package io.regionevent.regioneventbackend.domain.image.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Service
public class ImageObjectService {

    private final ImageObjectRepository imageObjectRepository;

    public ImageObjectService(ImageObjectRepository imageObjectRepository) {
        this.imageObjectRepository = imageObjectRepository;
    }

    public ImageObject createUploadCandidate(
        String objectKey,
        AppUser createdByUser,
        Region region,
        String mediaType,
        long byteSize,
        String checksum,
        Instant uploadExpiresAt
    ) {
        return imageObjectRepository.saveAndFlush(ImageObject.createUploadCandidate(
            objectKey,
            createdByUser,
            region,
            mediaType,
            byteSize,
            checksum,
            uploadExpiresAt
        ));
    }

    public Optional<DeletePendingImageObject> markDeletePendingIfUnreferenced(
        ImageObject previousImageObject,
        ImageObject replacementImageObject
    ) {
        if (previousImageObject == null) {
            return Optional.empty();
        }
        if (previousImageObject.getImageObjectId()
            .equals(replacementImageObject.getImageObjectId())) {
            return Optional.empty();
        }
        int updatedCount = imageObjectRepository.markActiveObjectDeletePendingWithoutDirectReferences(
            previousImageObject.getImageObjectId(),
            ImageLifecycleStatus.ACTIVE,
            ImageLifecycleStatus.DELETE_PENDING
        );
        if (updatedCount > 0) {
            previousImageObject.markDeletePending();
            return Optional.of(DeletePendingImageObject.from(previousImageObject));
        }
        return Optional.empty();
    }

    public ImageObject findActiveForUpdate(Long imageObjectId) {
        ImageObject imageObject = imageObjectRepository.findByImageObjectId(imageObjectId)
            .orElseThrow(() -> new IllegalStateException("representative image object must exist"));
        if (imageObject.getLifecycleStatus() != ImageLifecycleStatus.ACTIVE) {
            throw new IllegalStateException("representative image object must be active");
        }
        return imageObject;
    }

    public Optional<DeletePendingImageObject> markDeletePendingIfUnreferenced(
        ImageObject imageObject
    ) {
        int updatedCount = imageObjectRepository.markActiveObjectDeletePendingWithoutDirectReferences(
            imageObject.getImageObjectId(),
            ImageLifecycleStatus.ACTIVE,
            ImageLifecycleStatus.DELETE_PENDING
        );
        if (updatedCount == 0) {
            return Optional.empty();
        }
        imageObject.markDeletePending();
        return Optional.of(DeletePendingImageObject.from(imageObject));
    }

    public record DeletePendingImageObject(
        Long imageObjectId,
        String objectKey
    ) {

        private static DeletePendingImageObject from(ImageObject imageObject) {
            return new DeletePendingImageObject(
                imageObject.getImageObjectId(),
                imageObject.getObjectKey()
            );
        }
    }
}
