package io.regionevent.regioneventbackend.infra.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway;
import io.regionevent.regioneventbackend.domain.image.service.PresignedImageUploadSettings;

@Configuration
@EnableConfigurationProperties(S3StorageProperties.class)
public class ImageStorageConfig {

    @Bean
    public PresignedImageUploadSettings presignedImageUploadSettings(S3StorageProperties properties) {
        return new PresignedImageUploadSettings(properties.presignedPutUrlTtl());
    }

    @Bean
    @ConditionalOnProperty(prefix = "storage.s3", name = "enabled", havingValue = "true")
    public S3Presigner s3Presigner(S3StorageProperties properties) {
        return S3Presigner.builder()
            .region(Region.of(requireNotBlank(properties.region(), "storage.s3.region")))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "storage.s3", name = "enabled", havingValue = "true")
    public S3Client s3Client(S3StorageProperties properties) {
        return S3Client.builder()
            .region(Region.of(requireNotBlank(properties.region(), "storage.s3.region")))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "storage.s3", name = "enabled", havingValue = "true")
    public ImageStorageGateway s3ImageStorageGateway(
        S3StorageProperties properties,
        S3Presigner s3Presigner,
        S3Client s3Client
    ) {
        return new S3ImageStorageClient(
            requireNotBlank(properties.bucketName(), "storage.s3.bucket-name"),
            properties.presignedPutUrlTtl(),
            s3Presigner,
            s3Client
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
}
