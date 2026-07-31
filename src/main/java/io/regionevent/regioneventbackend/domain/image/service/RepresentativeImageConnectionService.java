package io.regionevent.regioneventbackend.domain.image.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class RepresentativeImageConnectionService {

    private final ImageObjectRepository imageObjectRepository;
    private final ImageStorageGateway imageStorageGateway;
    private final Clock clock;

    public RepresentativeImageConnectionService(
        ImageObjectRepository imageObjectRepository,
        ImageStorageGateway imageStorageGateway,
        Clock clock
    ) {
        this.imageObjectRepository = imageObjectRepository;
        this.imageStorageGateway = imageStorageGateway;
        this.clock = clock;
    }

    @Transactional
    public ImageObject validateAndMarkConnected(
        Long imageObjectId,
        Long operatorUserId,
        Long regionId
    ) {
        ImageObject imageObject = imageObjectRepository.findByIdForUpdate(imageObjectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        Instant now = clock.instant();
        validateConnectionCandidate(imageObject, operatorUserId, regionId, now);
        ImageObjectMetadata metadata = headObject(imageObject);
        validateUploadedObject(imageObject, metadata);
        imageObject.markLinked(now);
        return imageObject;
    }

    private static void validateConnectionCandidate(
        ImageObject imageObject,
        Long operatorUserId,
        Long regionId,
        Instant now
    ) {
        if (!imageObject.isOwnedBy(operatorUserId)
            || !imageObject.isScopedTo(regionId)
            || !imageObject.isConnectableAt(now)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private ImageObjectMetadata headObject(ImageObject imageObject) {
        try {
            return imageStorageGateway.headObject(imageObject.getObjectKey());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, exception);
        }
    }

    private static void validateUploadedObject(ImageObject imageObject, ImageObjectMetadata metadata) {
        if (metadata.checksumSha256() == null
            || !metadata.checksumSha256().equals(imageObject.getChecksum())
            || metadata.contentLength() != imageObject.getByteSize()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
