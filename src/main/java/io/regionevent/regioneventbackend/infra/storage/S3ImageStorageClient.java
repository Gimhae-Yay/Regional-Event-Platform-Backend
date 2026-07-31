package io.regionevent.regioneventbackend.infra.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import io.regionevent.regioneventbackend.domain.image.service.ImageObjectMetadata;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway;
import io.regionevent.regioneventbackend.domain.image.service.PresignedUpload;

public class S3ImageStorageClient implements ImageStorageGateway {

    private final String bucketName;
    private final Duration presignedPutUrlTtl;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    public S3ImageStorageClient(
        String bucketName,
        Duration presignedPutUrlTtl,
        S3Presigner s3Presigner,
        S3Client s3Client
    ) {
        this.bucketName = bucketName;
        this.presignedPutUrlTtl = presignedPutUrlTtl;
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
    }

    @Override
    public PresignedUpload createPresignedPutUpload(
        String objectKey,
        String mediaType,
        long byteSize,
        String checksum,
        Instant expiresAt
    ) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(objectKey)
            .contentType(mediaType)
            .contentLength(byteSize)
            .checksumSHA256(checksum)
            .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(presignedPutUrlTtl)
            .putObjectRequest(putObjectRequest)
            .build();
        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        Map<String, String> uploadHeaders = new LinkedHashMap<>();
        uploadHeaders.put("Content-Type", mediaType);
        uploadHeaders.put("Content-Length", Long.toString(byteSize));
        uploadHeaders.put("x-amz-checksum-sha256", checksum);

        return new PresignedUpload(
            presignedRequest.url().toString(),
            expiresAt,
            uploadHeaders
        );
    }

    @Override
    public ImageObjectMetadata headObject(String objectKey) {
        HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
            .bucket(bucketName)
            .key(objectKey)
            .checksumMode(ChecksumMode.ENABLED)
            .build());
        return new ImageObjectMetadata(response.checksumSHA256(), response.contentLength());
    }

    @Override
    public void deleteObject(String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(objectKey)
            .build());
    }
}
