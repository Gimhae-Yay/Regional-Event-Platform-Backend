package io.regionevent.regioneventbackend.infra.storage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageException;

class FakeImageStorageClient implements ImageStorageGateway {

    private final String baseUrl;
    private final Clock clock;
    private final Duration presignedUrlTtl;
    private final ConcurrentMap<String, StoredObjectMetadata> storedObjects = new ConcurrentHashMap<>();

    FakeImageStorageClient(
        String baseUrl,
        Clock clock,
        Duration presignedUrlTtl
    ) {
        this.baseUrl = removeTrailingSlash(baseUrl);
        this.clock = clock;
        this.presignedUrlTtl = presignedUrlTtl;
    }

    @Override
    public PresignedUpload createPresignedPutUpload(
        String objectKey,
        String mediaType,
        long byteSize,
        String checksum
    ) {
        storedObjects.put(objectKey, new StoredObjectMetadata(byteSize, checksum));
        return new PresignedUpload(
            createObjectUrl(objectKey),
            expiresAt(),
            Map.of(
                "Content-Type", mediaType,
                "Content-Length", Long.toString(byteSize),
                "x-amz-checksum-sha256", checksum
            )
        );
    }

    @Override
    public StoredObjectMetadata findMetadata(String objectKey) {
        StoredObjectMetadata metadata = storedObjects.get(objectKey);
        if (metadata == null) {
            throw new ImageStorageException("Fake image object does not exist");
        }
        return metadata;
    }

    @Override
    public PresignedViewUrl createPresignedGetUrl(String objectKey) {
        return new PresignedViewUrl(createObjectUrl(objectKey), expiresAt());
    }

    @Override
    public void delete(String objectKey) {
        storedObjects.remove(objectKey);
    }

    private String createObjectUrl(String objectKey) {
        String encodedObjectKey = URLEncoder.encode(objectKey, StandardCharsets.UTF_8);
        return baseUrl + "/" + encodedObjectKey;
    }

    private Instant expiresAt() {
        return clock.instant().plus(presignedUrlTtl);
    }

    private static String removeTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
