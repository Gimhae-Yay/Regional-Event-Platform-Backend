package io.regionevent.regioneventbackend.infra.storage;

import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.PresignedUpload;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Configuration
@EnableConfigurationProperties(ImageStorageConfig.S3StorageProperties.class)
public class ImageStorageConfig {

    @Bean
    @ConditionalOnProperty(prefix = "storage.s3", name = "enabled", havingValue = "true")
    public ImageStorageGateway s3ImageStorageGateway(
        S3StorageProperties properties,
        S3Presigner s3Presigner
    ) {
        return new S3ImageStorageClient(
            requireNotBlank(properties.bucketName(), "storage.s3.bucket-name"),
            properties.presignedPutUrlTtl(),
            s3Presigner
        );
    }

    @Bean
    @ConditionalOnMissingBean(ImageStorageGateway.class)
    public ImageStorageGateway disabledImageStorageGateway() {
        return new DisabledImageStorageClient();
    }

    private static String requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must not be null or blank");
        }
        return value;
    }

    @ConfigurationProperties(prefix = "storage.s3")
    public record S3StorageProperties(
        boolean enabled,
        String bucketName,
        Duration presignedPutUrlTtl
    ) {

        private static final Duration DEFAULT_PRESIGNED_PUT_URL_TTL = Duration.ofMinutes(10);

        public S3StorageProperties {
            if (presignedPutUrlTtl == null) {
                presignedPutUrlTtl = DEFAULT_PRESIGNED_PUT_URL_TTL;
            }
        }
    }

    private static class DisabledImageStorageClient implements ImageStorageGateway {

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
    }
}
