package io.regionevent.regioneventbackend.domain.image.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.PresignedViewUrl;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class RepresentativeImageViewUrlService {

    private final ImageStorageGateway imageStorageGateway;

    public RepresentativeImageViewUrlService(ImageStorageGateway imageStorageGateway) {
        this.imageStorageGateway = imageStorageGateway;
    }

    @Transactional(readOnly = true)
    public RepresentativeImageViewUrl createViewUrl(ImageObject imageObject) {
        validateLinkedActiveImage(imageObject);
        try {
            PresignedViewUrl presignedViewUrl =
                imageStorageGateway.createPresignedGetUrl(imageObject.getObjectKey());
            return new RepresentativeImageViewUrl(
                presignedViewUrl.url(),
                presignedViewUrl.expiresAt()
            );
        } catch (ImageStorageException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, exception);
        }
    }

    private void validateLinkedActiveImage(ImageObject imageObject) {
        if (imageObject == null
            || imageObject.getLifecycleStatus() != ImageLifecycleStatus.ACTIVE
            || imageObject.getLinkedAt() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
