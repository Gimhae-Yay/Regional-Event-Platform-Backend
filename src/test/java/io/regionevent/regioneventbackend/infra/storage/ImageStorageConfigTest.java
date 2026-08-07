package io.regionevent.regioneventbackend.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageException;

class ImageStorageConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(ImageStorageConfig.class)
        .withBean(Clock.class, Clock::systemUTC);

    @Test
    void imageStorageGateway_storageS3Enabled_createsS3GatewayAndClients() {
        contextRunner
            .withPropertyValues(
                "storage.s3.enabled=true",
                "storage.s3.bucket-name=bucket",
                "storage.s3.region=ap-northeast-2"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(ImageStorageGateway.class);
                assertThat(context).hasSingleBean(S3Client.class);
                assertThat(context).hasSingleBean(S3Presigner.class);
                assertThat(context.getBean(ImageStorageGateway.class)).isInstanceOf(S3ImageStorageClient.class);
            });
    }

    @Test
    void imageStorageGateway_storageS3Disabled_createsDisabledGatewayOnly() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ImageStorageGateway.class);
            assertThat(context).doesNotHaveBean(S3Client.class);
            assertThat(context).doesNotHaveBean(S3Presigner.class);
            assertThat(context.getBean(ImageStorageGateway.class)).isNotInstanceOf(S3ImageStorageClient.class);
        });
    }

    @Test
    void imageStorageGateway_storageFakeEnabled_createsFakeGatewayOnly() {
        contextRunner
            .withPropertyValues(
                "storage.fake.enabled=true",
                "storage.fake.base-url=http://localhost:8080/fake-images"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(ImageStorageGateway.class);
                assertThat(context).doesNotHaveBean(S3Client.class);
                assertThat(context).doesNotHaveBean(S3Presigner.class);
                assertThat(context.getBean(ImageStorageGateway.class)).isInstanceOf(FakeImageStorageClient.class);
            });
    }

    @Test
    void imageStorageGateway_storageS3AndFakeEnabled_createsS3GatewayOnly() {
        contextRunner
            .withPropertyValues(
                "storage.s3.enabled=true",
                "storage.s3.bucket-name=bucket",
                "storage.s3.region=ap-northeast-2",
                "storage.fake.enabled=true",
                "storage.fake.base-url=http://localhost:8080/fake-images"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(ImageStorageGateway.class);
                assertThat(context).hasSingleBean(S3Client.class);
                assertThat(context).hasSingleBean(S3Presigner.class);
                assertThat(context.getBean(ImageStorageGateway.class)).isInstanceOf(S3ImageStorageClient.class);
            });
    }

    @Test
    void s3StorageProperties_whenUrlTtlsAreMissing_usesDefaults() {
        ImageStorageConfig.S3StorageProperties properties = new ImageStorageConfig.S3StorageProperties(
            true,
            "bucket",
            "ap-northeast-2",
            null,
            null
        );

        assertThat(properties.presignedPutUrlTtl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.presignedGetUrlTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void fakeImageStorageClient_presignedUploadCreated_storesMetadataAndCreatesViewUrl() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), Clock.systemUTC().getZone());
        FakeImageStorageClient client = new FakeImageStorageClient(
            "http://localhost:8080/fake-images/",
            clock,
            Duration.ofMinutes(5)
        );

        ImageStorageGateway.PresignedUpload upload = client.createPresignedPutUpload(
            "k6/local/image.jpg",
            "image/jpeg",
            1024L,
            "checksum"
        );

        assertThat(upload.uploadUrl()).isEqualTo(
            "http://localhost:8080/fake-images/k6%2Flocal%2Fimage.jpg"
        );
        assertThat(upload.expiresAt()).isEqualTo(Instant.parse("2026-08-06T00:05:00Z"));
        assertThat(client.findMetadata("k6/local/image.jpg"))
            .isEqualTo(new ImageStorageGateway.StoredObjectMetadata(1024L, "checksum"));
        assertThat(client.createPresignedGetUrl("k6/local/image.jpg").url())
            .isEqualTo("http://localhost:8080/fake-images/k6%2Flocal%2Fimage.jpg");

        client.delete("k6/local/image.jpg");

        assertThatThrownBy(() -> client.findMetadata("k6/local/image.jpg"))
            .isInstanceOf(ImageStorageException.class);
    }
}
