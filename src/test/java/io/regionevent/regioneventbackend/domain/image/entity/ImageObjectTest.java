package io.regionevent.regioneventbackend.domain.image.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;

class ImageObjectTest {

    @Test
    void markLinked_whenUploadCandidateIsConnectable_setsLinkedAtAndClearsRequester() {
        Instant expiresAt = Instant.parse("2026-07-30T01:00:00Z");
        Instant linkedAt = Instant.parse("2026-07-30T00:59:00Z");
        AppUser operator = newOperator();
        ImageObject imageObject = createUploadCandidate(operator, expiresAt);

        imageObject.markLinked(linkedAt);

        assertThat(imageObject.getLinkedAt()).isEqualTo(linkedAt);
        assertThat(imageObject.getCreatedByUser()).isNull();
    }

    @Test
    void markLinked_whenUploadCandidateIsExpired_rejectsTransition() {
        Instant expiresAt = Instant.parse("2026-07-30T01:00:00Z");
        AppUser operator = newOperator();
        ImageObject imageObject = createUploadCandidate(operator, expiresAt);

        assertThat(imageObject.isConnectableAt(expiresAt)).isFalse();
        assertThatThrownBy(() -> imageObject.markLinked(expiresAt))
            .isInstanceOf(IllegalStateException.class);
        assertThat(imageObject.getLinkedAt()).isNull();
        assertThat(imageObject.getCreatedByUser()).isEqualTo(operator);
    }

    @Test
    void markLinked_whenImageObjectIsDeletePending_rejectsTransition() {
        AppUser operator = newOperator();
        ImageObject imageObject = createUploadCandidate(
            operator,
            Instant.parse("2026-07-30T01:00:00Z")
        );
        imageObject.markDeletePending();

        assertThatThrownBy(() -> imageObject.markLinked(Instant.parse("2026-07-30T00:59:00Z")))
            .isInstanceOf(IllegalStateException.class);
        assertThat(imageObject.getLinkedAt()).isNull();
        assertThat(imageObject.getCreatedByUser()).isEqualTo(operator);
    }

    @Test
    void constructor_whenObjectKeyIsNullOrBlank_rejectsValue() {
        assertThatThrownBy(
            () -> newImageObject(null, "image/webp", "sha256:test", ImageLifecycleStatus.ACTIVE, 1L, 0)
        )
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
            () -> newImageObject(" ", "image/webp", "sha256:test", ImageLifecycleStatus.ACTIVE, 1L, 0)
        )
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_whenMediaTypeIsNullOrBlank_rejectsValue() {
        assertThatThrownBy(
            () -> newImageObject("content/test.webp", null, "sha256:test", ImageLifecycleStatus.ACTIVE, 1L, 0)
        )
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
            () -> newImageObject("content/test.webp", "\t", "sha256:test", ImageLifecycleStatus.ACTIVE, 1L, 0)
        )
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_whenChecksumIsNullOrBlank_rejectsValue() {
        assertThatThrownBy(
            () -> newImageObject("content/test.webp", "image/webp", null, ImageLifecycleStatus.ACTIVE, 1L, 0)
        )
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
            () -> newImageObject("content/test.webp", "image/webp", "\n", ImageLifecycleStatus.ACTIVE, 1L, 0)
        )
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_whenLifecycleStatusIsNull_rejectsValue() {
        assertThatThrownBy(
            () -> newImageObject("content/test.webp", "image/webp", "sha256:test", null, 1L, 0)
        )
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_whenByteSizeIsNegative_rejectsValue() {
        assertThatThrownBy(
            () -> newImageObject("content/test.webp", "image/webp", "sha256:test", ImageLifecycleStatus.ACTIVE, -1L, 0)
        )
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_whenDeleteAttemptCountIsNegative_rejectsValue() {
        assertThatThrownBy(
            () -> newImageObject(
                "content/test.webp",
                "image/webp",
                "sha256:test",
                ImageLifecycleStatus.DELETE_PENDING,
                1L,
                -1
            )
        )
            .isInstanceOf(IllegalArgumentException.class);
    }

    private ImageObject createUploadCandidate(AppUser operator, Instant expiresAt) {
        return ImageObject.createUploadCandidate(
            "content/upload-candidate.webp",
            operator,
            new Region("GIMHAE", "김해시", true),
            "image/webp",
            1024L,
            "sha256:upload",
            expiresAt
        );
    }

    private AppUser newOperator() {
        return new AppUser(
            "operator@example.com",
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        );
    }

    private ImageObject newImageObject(
        String objectKey,
        String mediaType,
        String checksum,
        ImageLifecycleStatus lifecycleStatus,
        long byteSize,
        int deleteAttemptCount
    ) {
        return new ImageObject(
            objectKey,
            mediaType,
            byteSize,
            checksum,
            lifecycleStatus,
            deleteAttemptCount,
            null
        );
    }
}
