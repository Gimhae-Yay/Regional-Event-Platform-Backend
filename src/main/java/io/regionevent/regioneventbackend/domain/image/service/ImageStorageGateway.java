package io.regionevent.regioneventbackend.domain.image.service;

import java.time.Instant;
import java.util.Map;

public interface ImageStorageGateway {

    PresignedUpload createPresignedPutUpload(
        String objectKey,
        String mediaType,
        long byteSize,
        String checksum
    );

    StoredObjectMetadata findMetadata(String objectKey);

    void delete(String objectKey);

    record PresignedUpload(
        String uploadUrl,
        Instant expiresAt,
        Map<String, String> uploadHeaders
    ) {
    }

    record StoredObjectMetadata(
        long byteSize,
        String checksum
    ) {
    }
}
