package io.regionevent.regioneventbackend.domain.image.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class ImageObjectRepositoryTest {

    private final ImageObjectRepository imageObjectRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ImageObjectRepositoryTest(
        ImageObjectRepository imageObjectRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.imageObjectRepository = imageObjectRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.jdbcTemplate = jdbcTemplate;
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
        assertThat(savedImageObject.getMediaType()).isEqualTo("image/webp");
        assertThat(savedImageObject.getByteSize()).isEqualTo(1024L);
        assertThat(savedImageObject.getChecksum()).isEqualTo("sha256:abc");
        assertThat(savedImageObject.getLifecycleStatus()).isEqualTo(ImageLifecycleStatus.DELETE_PENDING);
        assertThat(savedImageObject.getDeleteAttemptCount()).isEqualTo(2);
        assertThat(savedImageObject.getLastDeleteAttemptedAt()).isEqualTo(lastDeleteAttemptedAt);
        assertThat(savedImageObject.getCreatedAt()).isNotNull();
    }

    @Test
    void save_whenUploadCandidate_persistsUploadContext() {
        Region region = regionRepository.saveAndFlush(new Region("UPLOAD", "김해시", true));
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "upload-operator@example.com",
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        Instant expiresAt = Instant.parse("2026-07-30T01:00:00Z");
        ImageObject imageObject = ImageObject.createUploadCandidate(
            "content/upload-candidate.webp",
            operator,
            region,
            "image/webp",
            1024L,
            "sha256:upload",
            expiresAt
        );

        ImageObject savedImageObject = imageObjectRepository.saveAndFlush(imageObject);

        assertThat(savedImageObject.getCreatedByUser().getUserId()).isEqualTo(operator.getUserId());
        assertThat(savedImageObject.getRegion().getRegionId()).isEqualTo(region.getRegionId());
        assertThat(savedImageObject.getUploadExpiresAt()).isEqualTo(expiresAt);
        assertThat(savedImageObject.getLinkedAt()).isNull();
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
    void save_whenByteSizeIsNegative_violatesDatabaseCheckConstraint() {
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
                INSERT INTO image_object (
                    object_key,
                    media_type,
                    byte_size,
                    checksum,
                    lifecycle_status,
                    delete_attempt_count,
                    last_delete_attempted_at,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
            "content/negative.webp",
            "image/webp",
            -1L,
            "sha256:test",
            "ACTIVE",
            0,
            null,
            Timestamp.from(Instant.parse("2026-07-29T00:00:00Z"))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_whenDeleteAttemptCountIsNegative_violatesDatabaseCheckConstraint() {
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
                INSERT INTO image_object (
                    object_key,
                    media_type,
                    byte_size,
                    checksum,
                    lifecycle_status,
                    delete_attempt_count,
                    last_delete_attempted_at,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
            "content/negative-attempt.webp",
            "image/webp",
            1L,
            "sha256:test",
            "ACTIVE",
            -1,
            null,
            Timestamp.from(Instant.parse("2026-07-29T00:00:00Z"))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void flywayMigration_whenApplied_createsDeletionRetryIndex() {
        List<String> indexedColumns = jdbcTemplate.query(
            """
                SELECT COLUMN_NAME
                FROM INFORMATION_SCHEMA.INDEX_COLUMNS
                WHERE TABLE_NAME = 'IMAGE_OBJECT'
                  AND INDEX_NAME = 'IDX_IMAGE_OBJECT_LIFECYCLE_STATUS_LAST_DELETE_ATTEMPTED_AT'
                ORDER BY ORDINAL_POSITION
                """,
            (resultSet, rowNumber) -> resultSet.getString("COLUMN_NAME")
        );

        assertThat(indexedColumns)
            .containsExactly("LIFECYCLE_STATUS", "LAST_DELETE_ATTEMPTED_AT");
    }

    @Test
    void flywayMigration_whenApplied_createsUploadCandidateIndex() {
        List<String> indexedColumns = jdbcTemplate.query(
            """
                SELECT COLUMN_NAME
                FROM INFORMATION_SCHEMA.INDEX_COLUMNS
                WHERE TABLE_NAME = 'IMAGE_OBJECT'
                  AND INDEX_NAME = 'IDX_IMAGE_OBJECT_UPLOAD_CANDIDATE'
                ORDER BY ORDINAL_POSITION
                """,
            (resultSet, rowNumber) -> resultSet.getString("COLUMN_NAME")
        );

        assertThat(indexedColumns)
            .containsExactly(
                "REGION_ID",
                "CREATED_BY_USER_ID",
                "LIFECYCLE_STATUS",
                "UPLOAD_EXPIRES_AT",
                "LINKED_AT"
            );
    }

    @Test
    void constructor_whenObjectKeyIsNullOrBlank_rejectsValue() {
        assertThatThrownBy(() -> createImageObject(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> createImageObject(" "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_whenMediaTypeIsNullOrBlank_rejectsValue() {
        assertThatThrownBy(() -> createImageObjectWithMediaType(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> createImageObjectWithMediaType("\t"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_whenChecksumIsNullOrBlank_rejectsValue() {
        assertThatThrownBy(() -> createImageObjectWithChecksum(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> createImageObjectWithChecksum("\n"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_whenLifecycleStatusIsNull_rejectsValue() {
        assertThatThrownBy(() -> new ImageObject(
            "content/test.webp",
            "image/webp",
            1L,
            "sha256:test",
            null,
            0,
            null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_whenByteSizeIsNegative_rejectsValue() {
        assertThatThrownBy(() -> new ImageObject(
            "content/negative.webp",
            "image/webp",
            -1L,
            "sha256:test",
            ImageLifecycleStatus.ACTIVE,
            0,
            null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_whenDeleteAttemptCountIsNegative_rejectsValue() {
        assertThatThrownBy(() -> new ImageObject(
            "content/negative-attempt.webp",
            "image/webp",
            1L,
            "sha256:test",
            ImageLifecycleStatus.DELETE_PENDING,
            -1,
            null
        )).isInstanceOf(IllegalArgumentException.class);
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

    private ImageObject createImageObjectWithMediaType(String mediaType) {
        return new ImageObject(
            "content/test.webp",
            mediaType,
            1L,
            "sha256:test",
            ImageLifecycleStatus.ACTIVE,
            0,
            null
        );
    }

    private ImageObject createImageObjectWithChecksum(String checksum) {
        return new ImageObject(
            "content/test.webp",
            "image/webp",
            1L,
            checksum,
            ImageLifecycleStatus.ACTIVE,
            0,
            null
        );
    }

}
