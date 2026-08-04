package io.regionevent.regioneventbackend.infra.storage;

import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import software.amazon.awssdk.core.exception.SdkException;
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

import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.PresignedUpload;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.PresignedViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.StoredObjectMetadata;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageException;

public class S3ImageStorageClient implements ImageStorageGateway {

    private final String bucketName;
    private final Clock clock;
    private final Duration presignedPutUrlTtl;
    private final Duration presignedGetUrlTtl;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public S3ImageStorageClient(
        String bucketName,
        Clock clock,
        Duration presignedPutUrlTtl,
        Duration presignedGetUrlTtl,
        S3Client s3Client,
        S3Presigner s3Presigner
    ) {
        this.bucketName = bucketName;
        this.clock = clock;
        this.presignedPutUrlTtl = presignedPutUrlTtl;
        this.presignedGetUrlTtl = presignedGetUrlTtl;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @Override
    public PresignedUpload createPresignedPutUpload(
        String objectKey,
        String mediaType,
        long byteSize,
        String checksum
    ) {
        try {
            Instant expiresAt = clock.instant().plus(presignedPutUrlTtl);
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(mediaType)
                .contentLength(byteSize)
                .checksumSHA256(checksum)
                .build();
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .putObjectRequest(putObjectRequest)
                .signatureDuration(presignedPutUrlTtl)
                .build();
            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
            URL uploadUrl = presignedRequest.url();

            Map<String, String> uploadHeaders = new LinkedHashMap<>();
            uploadHeaders.put("Content-Type", mediaType);
            uploadHeaders.put("Content-Length", Long.toString(byteSize));
            uploadHeaders.put("x-amz-checksum-sha256", checksum);

            return new PresignedUpload(
                uploadUrl.toString(),
                expiresAt,
                uploadHeaders
            );
        } catch (SdkException exception) {
            throw new ImageStorageException("Failed to create S3 presigned upload URL", exception);
        }
    }

    @Override
    public StoredObjectMetadata findMetadata(String objectKey) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .checksumMode(ChecksumMode.ENABLED)
                .build();
            HeadObjectResponse response = s3Client.headObject(request);
            return new StoredObjectMetadata(
                response.contentLength(),
                response.checksumSHA256()
            );
        } catch (SdkException exception) {
            throw new ImageStorageException("Failed to read S3 object metadata", exception);
        }
    }

    @Override
    public PresignedViewUrl createPresignedGetUrl(String objectKey) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .getObjectRequest(getObjectRequest)
                .signatureDuration(presignedGetUrlTtl)
                .build();
            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            URL viewUrl = presignedRequest.url();

            return new PresignedViewUrl(
                viewUrl.toString(),
                presignedRequest.expiration()
            );
        } catch (SdkException exception) {
            throw new ImageStorageException("Failed to create S3 presigned view URL", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
            s3Client.deleteObject(request);
        } catch (SdkException exception) {
            throw new ImageStorageException("Failed to delete S3 object", exception);
        }
    }

}
