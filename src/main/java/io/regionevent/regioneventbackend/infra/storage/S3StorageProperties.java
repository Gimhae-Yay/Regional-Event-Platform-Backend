package io.regionevent.regioneventbackend.infra.storage;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.s3")
public record S3StorageProperties(
    boolean enabled,
    String bucketName,
    String region,
    Duration presignedPutUrlTtl
) {

    private static final Duration DEFAULT_PRESIGNED_PUT_URL_TTL = Duration.ofMinutes(10);

    public S3StorageProperties {
        if (presignedPutUrlTtl == null) {
            presignedPutUrlTtl = DEFAULT_PRESIGNED_PUT_URL_TTL;
        }
    }
}
