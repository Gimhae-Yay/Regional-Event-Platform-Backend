package io.regionevent.regioneventbackend.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway;

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
}
