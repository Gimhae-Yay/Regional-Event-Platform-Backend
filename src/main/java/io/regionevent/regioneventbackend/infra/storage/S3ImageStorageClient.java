package io.regionevent.regioneventbackend.infra.storage;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.PresignedUpload;

public class S3ImageStorageClient implements ImageStorageGateway {

    private final String bucketName;
    private final Duration presignedPutUrlTtl;
    private final S3Presigner s3Presigner;

    public S3ImageStorageClient(
        String bucketName,
        Duration presignedPutUrlTtl,
        S3Presigner s3Presigner
    ) {
        this.bucketName = bucketName;
        this.presignedPutUrlTtl = presignedPutUrlTtl;
        this.s3Presigner = s3Presigner;
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
    }

}
