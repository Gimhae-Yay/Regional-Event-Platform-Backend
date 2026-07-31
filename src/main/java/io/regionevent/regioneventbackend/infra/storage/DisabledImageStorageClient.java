package io.regionevent.regioneventbackend.infra.storage;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.image.service.ImageObjectMetadata;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway;
import io.regionevent.regioneventbackend.domain.image.service.PresignedUpload;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

public class DisabledImageStorageClient implements ImageStorageGateway {

    @Override
    public PresignedUpload createPresignedPutUpload(
        String objectKey,
        String mediaType,
        long byteSize,
        String checksum,
        Instant expiresAt
    ) {
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Override
    public ImageObjectMetadata headObject(String objectKey) {
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Override
    public void deleteObject(String objectKey) {
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
