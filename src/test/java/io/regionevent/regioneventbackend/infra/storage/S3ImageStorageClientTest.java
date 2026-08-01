package io.regionevent.regioneventbackend.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.PresignedUpload;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.PresignedViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.StoredObjectMetadata;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageException;

class S3ImageStorageClientTest {

    private static final String BUCKET_NAME = "bucket";
    private static final Duration PRESIGNED_PUT_URL_TTL = Duration.ofMinutes(10);
    private static final Duration PRESIGNED_GET_URL_TTL = Duration.ofMinutes(5);
    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");

    @Test
    void createPresignedPutUpload_usesS3PresignerAndReturnsRequiredHeaders() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        S3Presigner s3Presigner = mock(S3Presigner.class);
        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        S3ImageStorageClient imageStorageClient = newImageStorageClient(s3Client, s3Presigner);
        when(presignedRequest.url()).thenReturn(URI.create("https://example.com/upload").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);

        PresignedUpload presignedUpload = imageStorageClient.createPresignedPutUpload(
            "contents/image.webp",
            "image/webp",
            123L,
            "checksum"
        );

        ArgumentCaptor<PutObjectPresignRequest> requestCaptor =
            ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(requestCaptor.capture());
        PutObjectRequest putObjectRequest = requestCaptor.getValue().putObjectRequest();
        Map<String, String> uploadHeaders = presignedUpload.uploadHeaders();
        assertThat(requestCaptor.getValue().signatureDuration()).isEqualTo(PRESIGNED_PUT_URL_TTL);
        assertThat(putObjectRequest.bucket()).isEqualTo(BUCKET_NAME);
        assertThat(putObjectRequest.key()).isEqualTo("contents/image.webp");
        assertThat(putObjectRequest.contentLength()).isEqualTo(123L);
        assertThat(putObjectRequest.contentType()).isEqualTo("image/webp");
        assertThat(putObjectRequest.checksumSHA256()).isEqualTo("checksum");
        assertThat(presignedUpload.uploadUrl()).isEqualTo("https://example.com/upload");
        assertThat(presignedUpload.expiresAt()).isEqualTo(NOW.plus(PRESIGNED_PUT_URL_TTL));
        assertThat(uploadHeaders).containsEntry("Content-Type", "image/webp");
        assertThat(uploadHeaders).containsEntry("Content-Length", "123");
        assertThat(uploadHeaders).containsEntry("x-amz-checksum-sha256", "checksum");
    }

    @Test
    void createPresignedGetUrl_usesS3PresignerAndReturnsUrlWithExpiresAt() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        S3Presigner s3Presigner = mock(S3Presigner.class);
        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        S3ImageStorageClient imageStorageClient = newImageStorageClient(s3Client, s3Presigner);
        when(presignedRequest.url()).thenReturn(URI.create("https://example.com/view").toURL());
        when(presignedRequest.expiration()).thenReturn(NOW.plus(PRESIGNED_GET_URL_TTL));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        PresignedViewUrl presignedViewUrl = imageStorageClient.createPresignedGetUrl("contents/image.webp");

        ArgumentCaptor<GetObjectPresignRequest> requestCaptor =
            ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(requestCaptor.capture());
        GetObjectRequest getObjectRequest = requestCaptor.getValue().getObjectRequest();
        assertThat(requestCaptor.getValue().signatureDuration()).isEqualTo(PRESIGNED_GET_URL_TTL);
        assertThat(getObjectRequest.bucket()).isEqualTo(BUCKET_NAME);
        assertThat(getObjectRequest.key()).isEqualTo("contents/image.webp");
        assertThat(presignedViewUrl.url()).isEqualTo("https://example.com/view");
        assertThat(presignedViewUrl.expiresAt()).isEqualTo(NOW.plus(PRESIGNED_GET_URL_TTL));
    }

    @Test
    void findMetadata_readsContentLengthAndChecksumFromS3HeadObject() {
        S3Client s3Client = mock(S3Client.class);
        S3Presigner s3Presigner = mock(S3Presigner.class);
        S3ImageStorageClient imageStorageClient = newImageStorageClient(s3Client, s3Presigner);
        HeadObjectResponse response = HeadObjectResponse.builder()
            .contentLength(123L)
            .checksumSHA256("checksum")
            .build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(response);

        StoredObjectMetadata metadata = imageStorageClient.findMetadata("contents/image.webp");

        ArgumentCaptor<HeadObjectRequest> requestCaptor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET_NAME);
        assertThat(requestCaptor.getValue().key()).isEqualTo("contents/image.webp");
        assertThat(requestCaptor.getValue().checksumMode()).isEqualTo(ChecksumMode.ENABLED);
        assertThat(metadata.byteSize()).isEqualTo(123L);
        assertThat(metadata.checksum()).isEqualTo("checksum");
    }

    @Test
    void delete_requestsS3DeleteObject() {
        S3Client s3Client = mock(S3Client.class);
        S3Presigner s3Presigner = mock(S3Presigner.class);
        S3ImageStorageClient imageStorageClient = newImageStorageClient(s3Client, s3Presigner);

        imageStorageClient.delete("contents/image.webp");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET_NAME);
        assertThat(requestCaptor.getValue().key()).isEqualTo("contents/image.webp");
    }

    @Test
    void createPresignedPutUpload_wrapsSdkException() {
        S3Client s3Client = mock(S3Client.class);
        S3Presigner s3Presigner = mock(S3Presigner.class);
        S3ImageStorageClient imageStorageClient = newImageStorageClient(s3Client, s3Presigner);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
            .thenThrow(SdkClientException.create("failed"));

        assertThatThrownBy(() -> imageStorageClient.createPresignedPutUpload(
            "contents/image.webp",
            "image/webp",
            123L,
            "checksum"
        ))
            .isInstanceOf(ImageStorageException.class)
            .hasCauseInstanceOf(SdkClientException.class);
    }

    @Test
    void findMetadata_wrapsSdkException() {
        S3Client s3Client = mock(S3Client.class);
        S3Presigner s3Presigner = mock(S3Presigner.class);
        S3ImageStorageClient imageStorageClient = newImageStorageClient(s3Client, s3Presigner);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
            .thenThrow(SdkClientException.create("failed"));

        assertThatThrownBy(() -> imageStorageClient.findMetadata("contents/image.webp"))
            .isInstanceOf(ImageStorageException.class)
            .hasCauseInstanceOf(SdkClientException.class);
    }

    @Test
    void createPresignedGetUrl_wrapsSdkException() {
        S3Client s3Client = mock(S3Client.class);
        S3Presigner s3Presigner = mock(S3Presigner.class);
        S3ImageStorageClient imageStorageClient = newImageStorageClient(s3Client, s3Presigner);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
            .thenThrow(SdkClientException.create("failed"));

        assertThatThrownBy(() -> imageStorageClient.createPresignedGetUrl("contents/image.webp"))
            .isInstanceOf(ImageStorageException.class)
            .hasCauseInstanceOf(SdkClientException.class);
    }

    @Test
    void delete_wrapsSdkException() {
        S3Client s3Client = mock(S3Client.class);
        S3Presigner s3Presigner = mock(S3Presigner.class);
        S3ImageStorageClient imageStorageClient = newImageStorageClient(s3Client, s3Presigner);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
            .thenThrow(SdkClientException.create("failed"));

        assertThatThrownBy(() -> imageStorageClient.delete("contents/image.webp"))
            .isInstanceOf(ImageStorageException.class)
            .hasCauseInstanceOf(SdkClientException.class);
    }

    private S3ImageStorageClient newImageStorageClient(
        S3Client s3Client,
        S3Presigner s3Presigner
    ) {
        return new S3ImageStorageClient(
            BUCKET_NAME,
            Clock.fixed(NOW, ZoneOffset.UTC),
            PRESIGNED_PUT_URL_TTL,
            PRESIGNED_GET_URL_TTL,
            s3Client,
            s3Presigner
        );
    }
}
