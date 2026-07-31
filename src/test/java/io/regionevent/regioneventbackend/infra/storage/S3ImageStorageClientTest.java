package io.regionevent.regioneventbackend.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import io.regionevent.regioneventbackend.domain.image.service.ImageObjectMetadata;

class S3ImageStorageClientTest {

    @Test
    void headObject_whenObjectExists_requestsChecksumModeAndReturnsMetadata() {
        S3Client s3Client = mock(S3Client.class);
        S3Presigner s3Presigner = mock(S3Presigner.class);
        S3ImageStorageClient imageStorageClient = new S3ImageStorageClient(
            "bucket",
            Duration.ofMinutes(10),
            s3Presigner,
            s3Client
        );
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
            .checksumSHA256("checksum")
            .contentLength(123L)
            .build());

        ImageObjectMetadata metadata = imageStorageClient.headObject("contents/image.webp");

        ArgumentCaptor<HeadObjectRequest> requestCaptor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(requestCaptor.capture());
        HeadObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("bucket");
        assertThat(request.key()).isEqualTo("contents/image.webp");
        assertThat(request.checksumMode()).isEqualTo(ChecksumMode.ENABLED);
        assertThat(metadata.checksumSha256()).isEqualTo("checksum");
        assertThat(metadata.contentLength()).isEqualTo(123L);
    }
}
