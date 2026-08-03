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

    PresignedViewUrl createPresignedGetUrl(String objectKey);

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

    record PresignedViewUrl(
        String url,
        Instant expiresAt
    ) {
    }
}
