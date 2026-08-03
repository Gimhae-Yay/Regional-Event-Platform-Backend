package io.regionevent.regioneventbackend.infra.storage;

import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.PresignedUpload;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.PresignedViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.StoredObjectMetadata;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageException;

@Configuration
@EnableConfigurationProperties(ImageStorageConfig.S3StorageProperties.class)
public class ImageStorageConfig {

    @Bean
    @ConditionalOnMissingBean(S3Client.class)
    @ConditionalOnProperty(prefix = "storage.s3", name = "enabled", havingValue = "true")
    public S3Client imageStorageS3Client(S3StorageProperties properties) {
        return S3Client.builder()
            .region(Region.of(requireNotBlank(properties.region(), "storage.s3.region")))
            .build();
    }

    @Bean
    @ConditionalOnMissingBean(S3Presigner.class)
    @ConditionalOnProperty(prefix = "storage.s3", name = "enabled", havingValue = "true")
    public S3Presigner imageStorageS3Presigner(S3StorageProperties properties) {
        return S3Presigner.builder()
            .region(Region.of(requireNotBlank(properties.region(), "storage.s3.region")))
            .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "storage.s3", name = "enabled", havingValue = "true")
    public ImageStorageGateway s3ImageStorageGateway(
        S3StorageProperties properties,
        Clock clock,
        S3Client s3Client,
        S3Presigner s3Presigner
    ) {
        return new S3ImageStorageClient(
            requireNotBlank(properties.bucketName(), "storage.s3.bucket-name"),
            clock,
            properties.presignedPutUrlTtl(),
            properties.presignedGetUrlTtl(),
            s3Client,
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

    private static ImageStorageException imageStorageDisabled() {
        return new ImageStorageException("Image storage is disabled");
    }

    @ConfigurationProperties(prefix = "storage.s3")
    public record S3StorageProperties(
        boolean enabled,
        String bucketName,
        String region,
        Duration presignedPutUrlTtl,
        Duration presignedGetUrlTtl
    ) {

        private static final Duration DEFAULT_PRESIGNED_PUT_URL_TTL = Duration.ofMinutes(10);
        private static final Duration DEFAULT_PRESIGNED_GET_URL_TTL = Duration.ofMinutes(5);

        public S3StorageProperties {
            if (presignedPutUrlTtl == null) {
                presignedPutUrlTtl = DEFAULT_PRESIGNED_PUT_URL_TTL;
            }
            if (presignedGetUrlTtl == null) {
                presignedGetUrlTtl = DEFAULT_PRESIGNED_GET_URL_TTL;
            }
        }
    }

    private static class DisabledImageStorageClient implements ImageStorageGateway {

        @Override
        public PresignedUpload createPresignedPutUpload(
            String objectKey,
            String mediaType,
            long byteSize,
            String checksum
        ) {
            throw imageStorageDisabled();
        }

        @Override
        public StoredObjectMetadata findMetadata(String objectKey) {
            throw imageStorageDisabled();
        }

        @Override
        public PresignedViewUrl createPresignedGetUrl(String objectKey) {
            throw imageStorageDisabled();
        }

        @Override
        public void delete(String objectKey) {
            throw imageStorageDisabled();
        }
    }
}
