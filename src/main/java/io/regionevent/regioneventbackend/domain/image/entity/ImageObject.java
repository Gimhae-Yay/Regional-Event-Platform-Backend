package io.regionevent.regioneventbackend.domain.image.entity;

import java.time.Instant;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

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

    @Column(name = "object_key", nullable = false, length = 768)
    private String objectKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private AppUser createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;

    @Column(name = "media_type", nullable = false, length = 100)
    private String mediaType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "checksum", nullable = false, length = 255)
    private String checksum;

    @Column(name = "upload_expires_at")
    private Instant uploadExpiresAt;

    @Column(name = "linked_at")
    private Instant linkedAt;

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
        this(
            objectKey,
            null,
            null,
            mediaType,
            byteSize,
            checksum,
            null,
            null,
            lifecycleStatus,
            deleteAttemptCount,
            lastDeleteAttemptedAt
        );
    }

    private ImageObject(
        String objectKey,
        AppUser createdByUser,
        Region region,
        String mediaType,
        long byteSize,
        String checksum,
        Instant uploadExpiresAt,
        Instant linkedAt,
        ImageLifecycleStatus lifecycleStatus,
        int deleteAttemptCount,
        Instant lastDeleteAttemptedAt
    ) {
        this.objectKey = requireNotBlank(objectKey, "objectKey");
        this.createdByUser = createdByUser;
        this.region = region;
        this.mediaType = requireNotBlank(mediaType, "mediaType");
        this.byteSize = requireNotNegative(byteSize, "byteSize");
        this.checksum = requireNotBlank(checksum, "checksum");
        this.uploadExpiresAt = uploadExpiresAt;
        this.linkedAt = linkedAt;
        this.lifecycleStatus = requireNotNull(lifecycleStatus, "lifecycleStatus");
        this.deleteAttemptCount = requireNotNegative(deleteAttemptCount, "deleteAttemptCount");
        this.lastDeleteAttemptedAt = lastDeleteAttemptedAt;
    }

    public static ImageObject createUploadCandidate(
        String objectKey,
        AppUser createdByUser,
        Region region,
        String mediaType,
        long byteSize,
        String checksum,
        Instant uploadExpiresAt
    ) {
        return new ImageObject(
            objectKey,
            requireNotNull(createdByUser, "createdByUser"),
            requireNotNull(region, "region"),
            mediaType,
            byteSize,
            checksum,
            requireNotNull(uploadExpiresAt, "uploadExpiresAt"),
            null,
            ImageLifecycleStatus.ACTIVE,
            0,
            null
        );
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

    public AppUser getCreatedByUser() {
        return createdByUser;
    }

    public Region getRegion() {
        return region;
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

    public Instant getUploadExpiresAt() {
        return uploadExpiresAt;
    }

    public Instant getLinkedAt() {
        return linkedAt;
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

    public boolean isOwnedBy(Long userId) {
        return createdByUser != null && createdByUser.getUserId().equals(userId);
    }

    public boolean isScopedTo(Long regionId) {
        return region != null && region.getRegionId().equals(regionId);
    }

    public boolean isConnectableAt(Instant now) {
        return lifecycleStatus == ImageLifecycleStatus.ACTIVE
            && linkedAt == null
            && uploadExpiresAt != null
            && uploadExpiresAt.isAfter(now);
    }

    public void markLinked(Instant linkedAt) {
        if (this.linkedAt != null) {
            throw new IllegalStateException("image object is already linked");
        }
        this.linkedAt = requireNotNull(linkedAt, "linkedAt");
        createdByUser = null;
    }

    public void markDeletePending() {
        if (lifecycleStatus == ImageLifecycleStatus.DELETE_PENDING) {
            return;
        }
        lifecycleStatus = ImageLifecycleStatus.DELETE_PENDING;
    }

    public void recordDeleteAttempt(Instant attemptedAt) {
        deleteAttemptCount++;
        lastDeleteAttemptedAt = requireNotNull(attemptedAt, "attemptedAt");
    }

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value;
    }

    private static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static long requireNotNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }

    private static int requireNotNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }
}
