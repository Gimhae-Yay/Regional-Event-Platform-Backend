package io.regionevent.regioneventbackend.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
    ImageObjectCleanupService.class,
    ImageObjectCleanupServiceTest.CleanupTestConfiguration.class
})
class ImageObjectCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    private final ImageObjectCleanupService imageObjectCleanupService;
    private final ImageObjectRepository imageObjectRepository;
    private final ContentRepository contentRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;
    private final RecordingImageStorageGateway imageStorageGateway;

    @Autowired
    ImageObjectCleanupServiceTest(
        ImageObjectCleanupService imageObjectCleanupService,
        ImageObjectRepository imageObjectRepository,
        ContentRepository contentRepository,
        ContentRevisionRepository contentRevisionRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager,
        RecordingImageStorageGateway imageStorageGateway
    ) {
        this.imageObjectCleanupService = imageObjectCleanupService;
        this.imageObjectRepository = imageObjectRepository;
        this.contentRepository = contentRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
        this.imageStorageGateway = imageStorageGateway;
    }

    @BeforeEach
    void setUp() {
        imageStorageGateway.reset();
    }

    @AfterEach
    void tearDown() {
        contentRevisionRepository.deleteAllInBatch();
        contentRepository.deleteAllInBatch();
        imageObjectRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
        regionRepository.deleteAllInBatch();
    }

    @Test
    void cleanupExpiredUnlinkedUploadCandidates_whenExpiredAndUnreferenced_deletesStorageAndRow() {
        ImageObject imageObject = saveUploadCandidate("content/expired.webp", Instant.now().minusSeconds(60));

        int deletedCount = imageObjectCleanupService.cleanupExpiredUnlinkedUploadCandidates();

        assertThat(deletedCount).isOne();
        assertThat(imageObjectRepository.existsById(imageObject.getImageObjectId())).isFalse();
        assertThat(imageStorageGateway.deletedObjectKeys()).containsExactly("content/expired.webp");
    }

    @Test
    void cleanupExpiredUnlinkedUploadCandidates_beforeStorageDeletion_marksDeletePending() {
        ImageObject imageObject = saveUploadCandidate("content/delete-order.webp", Instant.now().minusSeconds(60));
        List<ImageLifecycleStatus> observedStatuses = new ArrayList<>();
        imageStorageGateway.beforeDelete(() -> observedStatuses.add(
            imageObjectRepository.findById(imageObject.getImageObjectId()).orElseThrow().getLifecycleStatus()
        ));

        imageObjectCleanupService.cleanupExpiredUnlinkedUploadCandidates();

        assertThat(observedStatuses).containsExactly(ImageLifecycleStatus.DELETE_PENDING);
    }

    @Test
    void cleanupExpiredUnlinkedUploadCandidates_whenStorageDeletionFails_recordsRetryState() {
        ImageObject imageObject = saveUploadCandidate("content/delete-failure.webp", Instant.now().minusSeconds(60));
        imageStorageGateway.failDeleteFor(imageObject.getObjectKey());
        Instant beforeCleanup = Instant.now().minusSeconds(5);

        int deletedCount = imageObjectCleanupService.cleanupExpiredUnlinkedUploadCandidates();

        Instant afterCleanup = Instant.now().plusSeconds(5);
        ImageObject foundImageObject = imageObjectRepository.findById(imageObject.getImageObjectId()).orElseThrow();
        assertThat(deletedCount).isZero();
        assertThat(foundImageObject.getLifecycleStatus()).isEqualTo(ImageLifecycleStatus.DELETE_PENDING);
        assertThat(foundImageObject.getDeleteAttemptCount()).isOne();
        assertThat(foundImageObject.getLastDeleteAttemptedAt()).isBetween(beforeCleanup, afterCleanup);
        assertThat(imageStorageGateway.deletedObjectKeys()).containsExactly("content/delete-failure.webp");
    }

    @Test
    void cleanupExpiredUnlinkedUploadCandidates_whenDeletePendingRetrySucceeds_deletesRow() {
        ImageObject imageObject = saveUploadCandidate("content/delete-retry.webp", Instant.now().minusSeconds(60));
        imageStorageGateway.failDeleteFor(imageObject.getObjectKey());
        imageObjectCleanupService.cleanupExpiredUnlinkedUploadCandidates();
        imageStorageGateway.allowDeleteFor(imageObject.getObjectKey());

        int deletedCount = imageObjectCleanupService.cleanupExpiredUnlinkedUploadCandidates();

        assertThat(deletedCount).isOne();
        assertThat(imageObjectRepository.existsById(imageObject.getImageObjectId())).isFalse();
        assertThat(imageStorageGateway.deletedObjectKeys())
            .containsExactly("content/delete-retry.webp", "content/delete-retry.webp");
    }

    @Test
    void cleanupExpiredUnlinkedUploadCandidates_whenContentReferencesImage_keepsImageObject() {
        ImageObject imageObject = saveUploadCandidate("content/content-reference.webp", Instant.now().minusSeconds(60));
        Content content = saveContent("content-reference");
        content.assignRepresentativeImage(imageObject, NOW.minusSeconds(10));
        contentRepository.saveAndFlush(content);
        entityManager.clear();

        int deletedCount = imageObjectCleanupService.cleanupExpiredUnlinkedUploadCandidates();

        ImageObject foundImageObject = imageObjectRepository.findById(imageObject.getImageObjectId()).orElseThrow();
        assertThat(deletedCount).isZero();
        assertThat(foundImageObject.getLifecycleStatus()).isEqualTo(ImageLifecycleStatus.ACTIVE);
        assertThat(imageStorageGateway.deletedObjectKeys()).isEmpty();
    }

    @Test
    void cleanupExpiredUnlinkedUploadCandidates_whenRevisionReferencesImage_keepsImageObject() {
        ImageObject imageObject = saveUploadCandidate("content/revision-reference.webp", Instant.now().minusSeconds(60));
        Content content = saveContent("revision-reference");
        AppUser editor = saveUser("editor-revision-reference@example.com");
        ContentRevision contentRevision = contentRevisionRepository.saveAndFlush(newRevision(content, editor));
        contentRevision.assignCandidateImage(imageObject, NOW.minusSeconds(10));
        contentRevisionRepository.saveAndFlush(contentRevision);
        entityManager.clear();

        int deletedCount = imageObjectCleanupService.cleanupExpiredUnlinkedUploadCandidates();

        ImageObject foundImageObject = imageObjectRepository.findById(imageObject.getImageObjectId()).orElseThrow();
        assertThat(deletedCount).isZero();
        assertThat(foundImageObject.getLifecycleStatus()).isEqualTo(ImageLifecycleStatus.ACTIVE);
        assertThat(imageStorageGateway.deletedObjectKeys()).isEmpty();
    }

    @Test
    void cleanupExpiredUnlinkedUploadCandidates_whenImageAlreadyLinked_keepsImageObject() {
        ImageObject imageObject = saveUploadCandidate("content/linked.webp", Instant.now().minusSeconds(60));
        imageObject.markLinked(Instant.now().minusSeconds(120));
        imageObjectRepository.saveAndFlush(imageObject);

        int deletedCount = imageObjectCleanupService.cleanupExpiredUnlinkedUploadCandidates();

        ImageObject foundImageObject = imageObjectRepository.findById(imageObject.getImageObjectId()).orElseThrow();
        assertThat(deletedCount).isZero();
        assertThat(foundImageObject.getLifecycleStatus()).isEqualTo(ImageLifecycleStatus.ACTIVE);
        assertThat(foundImageObject.getLinkedAt()).isNotNull();
        assertThat(imageStorageGateway.deletedObjectKeys()).isEmpty();
    }

    @Test
    @Transactional
    void markDeletePending_whenCandidateIsLinkedAfterCandidateRead_skipsTransition() {
        Instant uploadExpiresAt = Instant.now().minusSeconds(60);
        ImageObject imageObject = saveUploadCandidate("content/linked-after-read.webp", uploadExpiresAt);
        List<Long> candidateIds = imageObjectRepository.findExpiredUnlinkedUploadCandidateIdsWithoutDirectReferences(
            ImageLifecycleStatus.ACTIVE,
            PageRequest.of(0, 100)
        );
        Content content = saveContent("linked-after-read");
        assertThat(candidateIds).containsExactly(imageObject.getImageObjectId());

        imageObject.markLinked(uploadExpiresAt.minusSeconds(1));
        content.assignRepresentativeImage(imageObject, uploadExpiresAt.minusSeconds(1));
        imageObjectRepository.saveAndFlush(imageObject);
        contentRepository.saveAndFlush(content);

        int updatedCount = imageObjectRepository.markExpiredUnlinkedUploadCandidateDeletePending(
            candidateIds.getFirst(),
            ImageLifecycleStatus.ACTIVE,
            ImageLifecycleStatus.DELETE_PENDING
        );

        ImageObject foundImageObject = imageObjectRepository.findById(imageObject.getImageObjectId()).orElseThrow();
        assertThat(updatedCount).isZero();
        assertThat(foundImageObject.getLifecycleStatus()).isEqualTo(ImageLifecycleStatus.ACTIVE);
        assertThat(foundImageObject.getLinkedAt()).isNotNull();
    }

    @Test
    void cleanupExpiredUnlinkedUploadCandidates_whenUploadNotExpired_keepsImageObject() {
        ImageObject imageObject = saveUploadCandidate("content/not-expired.webp", Instant.now().plusSeconds(3600));

        int deletedCount = imageObjectCleanupService.cleanupExpiredUnlinkedUploadCandidates();

        ImageObject foundImageObject = imageObjectRepository.findById(imageObject.getImageObjectId()).orElseThrow();
        assertThat(deletedCount).isZero();
        assertThat(foundImageObject.getLifecycleStatus()).isEqualTo(ImageLifecycleStatus.ACTIVE);
        assertThat(imageStorageGateway.deletedObjectKeys()).isEmpty();
    }

    @Test
    void cleanupExpiredUnlinkedUploadCandidates_whenDeletePendingBatchIsFull_stillProcessesExpiredActiveCandidate() {
        for (int index = 0; index < 101; index++) {
            saveDeletePendingImageObject("content/delete-pending-" + index + ".webp");
        }
        ImageObject imageObject = saveUploadCandidate("content/active-after-delete-pending.webp", Instant.now().minusSeconds(60));

        imageObjectCleanupService.cleanupExpiredUnlinkedUploadCandidates();

        assertThat(imageObjectRepository.existsById(imageObject.getImageObjectId())).isFalse();
        assertThat(imageStorageGateway.deletedObjectKeys()).contains("content/active-after-delete-pending.webp");
    }

    private ImageObject saveUploadCandidate(String objectKey, Instant uploadExpiresAt) {
        Region region = regionRepository.saveAndFlush(new Region("REGION-" + objectKey.hashCode(), "Region", true));
        AppUser operator = saveUser("operator-" + objectKey.hashCode() + "@example.com");
        return imageObjectRepository.saveAndFlush(ImageObject.createUploadCandidate(
            objectKey,
            operator,
            region,
            "image/webp",
            1024L,
            "m3vD5u5z9Q4p7nZf3s1q5u9w2x8a7b6c5d4e3f2g1h0=",
            uploadExpiresAt
        ));
    }

    private ImageObject saveDeletePendingImageObject(String objectKey) {
        return imageObjectRepository.saveAndFlush(new ImageObject(
            objectKey,
            "image/webp",
            1024L,
            "m3vD5u5z9Q4p7nZf3s1q5u9w2x8a7b6c5d4e3f2g1h0=",
            ImageLifecycleStatus.DELETE_PENDING,
            0,
            null
        ));
    }

    private Content saveContent(String uniqueSuffix) {
        Region region = regionRepository.saveAndFlush(new Region("CONTENT-" + uniqueSuffix, "Region", true));
        AppUser operator = saveUser("content-operator-" + uniqueSuffix + "@example.com");
        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PENDING,
            "Event " + uniqueSuffix,
            "Event description",
            "Location",
            "10:00-18:00",
            "055-123-4567",
            "Follow safety rules",
            "Ages 7 and up",
            "Comfortable clothes",
            "Cancel before the event starts",
            NOW.plusSeconds(86_400)
        ));
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "Operator",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private ContentRevision newRevision(Content content, AppUser editor) {
        return new ContentRevision(
            content,
            1,
            content.getVersionNo(),
            editor,
            ContentRevisionStatus.EDIT_REQUESTED,
            "Updated event",
            "Updated event description",
            "Updated location",
            "11:00-19:00",
            "055-987-6543",
            "Follow updated safety rules",
            "Ages 8 and up",
            "Indoor shoes",
            "Cancel before the updated event starts",
            null,
            NOW.minusSeconds(10),
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    @TestConfiguration
    static class CleanupTestConfiguration {

        @Bean
        RecordingImageStorageGateway imageStorageGateway() {
            return new RecordingImageStorageGateway();
        }
    }

    static class RecordingImageStorageGateway implements ImageStorageGateway {

        private final List<String> deletedObjectKeys = new ArrayList<>();
        private final Set<String> failingObjectKeys = new HashSet<>();
        private Runnable beforeDelete = () -> {
        };

        @Override
        public PresignedUpload createPresignedPutUpload(
            String objectKey,
            String mediaType,
            long byteSize,
            String checksum
        ) {
            throw new UnsupportedOperationException("not used in cleanup tests");
        }

        @Override
        public StoredObjectMetadata findMetadata(String objectKey) {
            throw new UnsupportedOperationException("not used in cleanup tests");
        }

        @Override
        public void delete(String objectKey) {
            beforeDelete.run();
            deletedObjectKeys.add(objectKey);
            if (failingObjectKeys.contains(objectKey)) {
                throw new ImageStorageException("delete failed");
            }
        }

        void failDeleteFor(String objectKey) {
            failingObjectKeys.add(objectKey);
        }

        void allowDeleteFor(String objectKey) {
            failingObjectKeys.remove(objectKey);
        }

        List<String> deletedObjectKeys() {
            return deletedObjectKeys;
        }

        void beforeDelete(Runnable beforeDelete) {
            this.beforeDelete = beforeDelete;
        }

        void reset() {
            deletedObjectKeys.clear();
            failingObjectKeys.clear();
            beforeDelete = () -> {
            };
        }
    }
}
