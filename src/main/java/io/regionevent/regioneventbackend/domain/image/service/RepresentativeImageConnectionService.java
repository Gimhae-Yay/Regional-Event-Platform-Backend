package io.regionevent.regioneventbackend.domain.image.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.StoredObjectMetadata;
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
        validateRequiredId(imageObjectId);
        validateRequiredId(operatorUserId);
        validateRequiredId(regionId);

        Instant now = clock.instant();
        ImageObject imageObject = imageObjectRepository.findByImageObjectId(imageObjectId)
            .orElseThrow(RepresentativeImageConnectionService::invalidInput);

        validateConnectableImageObject(imageObject, operatorUserId, regionId, now);
        validateStoredMetadata(imageObject);
        imageObject.markLinked(now);

        return imageObject;
    }

    private void validateConnectableImageObject(
        ImageObject imageObject,
        Long operatorUserId,
        Long regionId,
        Instant now
    ) {
        if (!imageObject.isOwnedBy(operatorUserId)
            || !imageObject.isScopedTo(regionId)
            || !imageObject.isConnectableAt(now)
        ) {
            throw invalidInput();
        }
    }

    private void validateStoredMetadata(ImageObject imageObject) {
        StoredObjectMetadata metadata = imageStorageGateway.findMetadata(imageObject.getObjectKey());
        if (metadata == null
            || metadata.byteSize() != imageObject.getByteSize()
            || !Objects.equals(metadata.checksum(), imageObject.getChecksum())
        ) {
            throw invalidInput();
        }
    }

    private void validateRequiredId(Long id) {
        if (id == null || id <= 0) {
            throw invalidInput();
        }
    }

    private static BusinessException invalidInput() {
        return new BusinessException(ErrorCode.INVALID_INPUT);
    }
}
