package io.regionevent.regioneventbackend.domain.image.service;

import java.time.Instant;
import java.util.Map;

public interface ImageStorageGateway {

    PresignedUpload createPresignedPutUpload(
        String objectKey,
        String mediaType,
        long byteSize,
        String checksum,
        Instant expiresAt
    );

    record PresignedUpload(
        String uploadUrl,
        Instant expiresAt,
        Map<String, String> uploadHeaders
    ) {
    }
}
