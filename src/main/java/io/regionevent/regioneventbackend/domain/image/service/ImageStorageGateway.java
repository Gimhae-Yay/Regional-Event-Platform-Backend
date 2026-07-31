package io.regionevent.regioneventbackend.domain.image.service;

import java.time.Instant;

public interface ImageStorageGateway {

    PresignedUpload createPresignedPutUpload(
        String objectKey,
        String mediaType,
        long byteSize,
        String checksum,
        Instant expiresAt
    );

    ImageObjectMetadata headObject(String objectKey);

    void deleteObject(String objectKey);
}
