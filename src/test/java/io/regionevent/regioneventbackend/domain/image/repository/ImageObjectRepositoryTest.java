package io.regionevent.regioneventbackend.domain.image.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class ImageObjectRepositoryTest {

    private final ImageObjectRepository imageObjectRepository;

    @Autowired
    ImageObjectRepositoryTest(ImageObjectRepository imageObjectRepository) {
        this.imageObjectRepository = imageObjectRepository;
    }

    @Test
    void save_whenValidImageObject_persistsMappedValues() {
        Instant lastDeleteAttemptedAt = Instant.parse("2026-07-29T00:00:00Z");
        ImageObject imageObject = new ImageObject(
            "content/abc.webp",
            "image/webp",
            1024L,
            "sha256:abc",
            ImageLifecycleStatus.DELETE_PENDING,
            2,
            lastDeleteAttemptedAt
        );

        ImageObject savedImageObject = imageObjectRepository.saveAndFlush(imageObject);

        assertThat(savedImageObject.getImageObjectId()).isNotNull();
        assertThat(savedImageObject.getObjectKey()).isEqualTo("content/abc.webp");
        assertThat(savedImageObject.getLifecycleStatus()).isEqualTo(ImageLifecycleStatus.DELETE_PENDING);
        assertThat(savedImageObject.getDeleteAttemptCount()).isEqualTo(2);
        assertThat(savedImageObject.getLastDeleteAttemptedAt()).isEqualTo(lastDeleteAttemptedAt);
        assertThat(savedImageObject.getCreatedAt()).isNotNull();
    }

    @Test
    void save_whenObjectKeyIsDuplicated_violatesUniqueConstraint() {
        ImageObject firstImageObject = createImageObject("content/duplicate.webp");
        ImageObject secondImageObject = createImageObject("content/duplicate.webp");

        imageObjectRepository.saveAndFlush(firstImageObject);

        assertThatThrownBy(() -> imageObjectRepository.saveAndFlush(secondImageObject))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_whenByteSizeIsNegative_violatesCheckConstraint() {
        ImageObject imageObject = new ImageObject(
            "content/negative.webp",
            "image/webp",
            -1L,
            "sha256:negative",
            ImageLifecycleStatus.ACTIVE,
            0,
            null
        );

        assertThatThrownBy(() -> imageObjectRepository.saveAndFlush(imageObject))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_whenDeleteAttemptCountIsNegative_violatesCheckConstraint() {
        ImageObject imageObject = new ImageObject(
            "content/negative-attempt.webp",
            "image/webp",
            1L,
            "sha256:negative-attempt",
            ImageLifecycleStatus.DELETE_PENDING,
            -1,
            null
        );

        assertThatThrownBy(() -> imageObjectRepository.saveAndFlush(imageObject))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ImageObject createImageObject(String objectKey) {
        return new ImageObject(
            objectKey,
            "image/webp",
            1L,
            "sha256:test",
            ImageLifecycleStatus.ACTIVE,
            0,
            null
        );
    }
}
