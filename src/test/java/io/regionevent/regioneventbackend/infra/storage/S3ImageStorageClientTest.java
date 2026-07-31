package io.regionevent.regioneventbackend.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.PresignedUpload;

class S3ImageStorageClientTest {

    @Test
    void createPresignedPutUpload_usesS3PresignerAndReturnsRequiredHeaders() throws Exception {
        S3Presigner s3Presigner = mock(S3Presigner.class);
        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        S3ImageStorageClient imageStorageClient = new S3ImageStorageClient(
            "bucket",
            Duration.ofMinutes(10),
            s3Presigner
        );
        Instant expiresAt = Instant.parse("2026-07-31T00:10:00Z");
        when(presignedRequest.url()).thenReturn(URI.create("https://example.com/upload").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);

        PresignedUpload presignedUpload = imageStorageClient.createPresignedPutUpload(
            "contents/image.webp",
            "image/webp",
            123L,
            "checksum",
            expiresAt
        );

        ArgumentCaptor<PutObjectPresignRequest> requestCaptor =
            ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(requestCaptor.capture());
        PutObjectRequest putObjectRequest = requestCaptor.getValue().putObjectRequest();
        Map<String, String> uploadHeaders = presignedUpload.uploadHeaders();
        assertThat(putObjectRequest.bucket()).isEqualTo("bucket");
        assertThat(putObjectRequest.key()).isEqualTo("contents/image.webp");
        assertThat(putObjectRequest.contentLength()).isEqualTo(123L);
        assertThat(putObjectRequest.contentType()).isEqualTo("image/webp");
        assertThat(putObjectRequest.checksumSHA256()).isEqualTo("checksum");
        assertThat(presignedUpload.uploadUrl()).isEqualTo("https://example.com/upload");
        assertThat(presignedUpload.expiresAt()).isEqualTo(expiresAt);
        assertThat(uploadHeaders).containsEntry("Content-Type", "image/webp");
        assertThat(uploadHeaders).containsEntry("Content-Length", "123");
        assertThat(uploadHeaders).containsEntry("x-amz-checksum-sha256", "checksum");
    }
}
