package io.regionevent.regioneventbackend.domain.image.entity;

import java.time.Instant;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "image_object",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_image_object_object_key",
        columnNames = "object_key"
    ),
    check = {
        @CheckConstraint(
            name = "ck_image_object_byte_size",
            constraint = "byte_size >= 0"
        ),
        @CheckConstraint(
            name = "ck_image_object_delete_attempt_count",
            constraint = "delete_attempt_count >= 0"
        )
    }
)
public class ImageObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_object_id")
    private Long imageObjectId;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    @Column(name = "media_type", nullable = false, length = 100)
    private String mediaType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "checksum", nullable = false, length = 255)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 30)
    private ImageLifecycleStatus lifecycleStatus;

    @Column(name = "delete_attempt_count", nullable = false)
    private int deleteAttemptCount;

    @Column(name = "last_delete_attempted_at")
    private Instant lastDeleteAttemptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ImageObject() {
    }

    public ImageObject(
        String objectKey,
        String mediaType,
        long byteSize,
        String checksum,
        ImageLifecycleStatus lifecycleStatus,
        int deleteAttemptCount,
        Instant lastDeleteAttemptedAt
    ) {
        this.objectKey = objectKey;
        this.mediaType = mediaType;
        this.byteSize = byteSize;
        this.checksum = checksum;
        this.lifecycleStatus = lifecycleStatus;
        this.deleteAttemptCount = deleteAttemptCount;
        this.lastDeleteAttemptedAt = lastDeleteAttemptedAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public Long getImageObjectId() {
        return imageObjectId;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getMediaType() {
        return mediaType;
    }

    public long getByteSize() {
        return byteSize;
    }

    public String getChecksum() {
        return checksum;
    }

    public ImageLifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public int getDeleteAttemptCount() {
        return deleteAttemptCount;
    }

    public Instant getLastDeleteAttemptedAt() {
        return lastDeleteAttemptedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
