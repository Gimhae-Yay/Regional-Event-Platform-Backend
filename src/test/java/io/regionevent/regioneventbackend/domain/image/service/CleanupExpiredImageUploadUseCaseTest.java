package io.regionevent.regioneventbackend.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
import io.regionevent.regioneventbackend.domain.content.service.ContentRepresentativeImageReferenceService;
import io.regionevent.regioneventbackend.domain.content.service.ContentRevisionImageReferenceService;
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
class CleanupExpiredImageUploadUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");
    private static final String CHECKSUM = Base64.getEncoder().encodeToString(new byte[32]);

    private final CleanupExpiredImageUploadUseCase useCase;
    private final ImageObjectRepository imageObjectRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final FakeImageStorageClient imageStorageClient;

    @Autowired
    CleanupExpiredImageUploadUseCaseTest(
        ImageObjectRepository imageObjectRepository,
        ContentRepository contentRepository,
        ContentRevisionRepository contentRevisionRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.imageObjectRepository = imageObjectRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.imageStorageClient = new FakeImageStorageClient();
        this.useCase = new CleanupExpiredImageUploadUseCase(
            new ImageObjectCleanupService(imageObjectRepository),
            new ContentRepresentativeImageReferenceService(contentRepository),
            new ContentRevisionImageReferenceService(contentRevisionRepository),
            imageStorageClient,
            transactionManager,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void cleanupExpiredUploadCandidates_whenCandidateIsExpired_deletesS3ObjectAndDatabaseRow() {
        ImageObject imageObject = saveUploadCandidate(NOW.minusSeconds(1));

        useCase.cleanupExpiredUploadCandidates();
        imageObjectRepository.flush();

        assertThat(imageStorageClient.deletedObjectKeys).containsExactly(imageObject.getObjectKey());
        assertThat(imageObjectRepository.findById(imageObject.getImageObjectId())).isEmpty();
    }

    @Test
    void cleanupExpiredUploadCandidates_whenCandidateIsNotExpired_keepsObject() {
        ImageObject imageObject = saveUploadCandidate(NOW.plusSeconds(60));

        useCase.cleanupExpiredUploadCandidates();

        assertThat(imageStorageClient.deletedObjectKeys).isEmpty();
        assertThat(imageObjectRepository.findById(imageObject.getImageObjectId())).isPresent();
    }

    @Test
    void cleanupExpiredUploadCandidates_whenDeleteFails_retriesDeletePendingObject() {
        ImageObject imageObject = saveUploadCandidate(NOW.minusSeconds(1));
        imageStorageClient.failNextDelete();

        useCase.cleanupExpiredUploadCandidates();
        imageObjectRepository.flush();
        ImageObject deletePendingObject = imageObjectRepository.findById(imageObject.getImageObjectId()).orElseThrow();
        assertThat(deletePendingObject.getLifecycleStatus()).isEqualTo(ImageLifecycleStatus.DELETE_PENDING);
        assertThat(deletePendingObject.getDeleteAttemptCount()).isEqualTo(1);
        assertThat(deletePendingObject.getLastDeleteAttemptedAt()).isEqualTo(NOW);

        useCase.cleanupExpiredUploadCandidates();
        imageObjectRepository.flush();

        assertThat(imageStorageClient.deletedObjectKeys).containsExactly(
            imageObject.getObjectKey(),
            imageObject.getObjectKey()
        );
        assertThat(imageObjectRepository.findById(imageObject.getImageObjectId())).isEmpty();
    }

    private ImageObject saveUploadCandidate(Instant expiresAt) {
        Region region = regionRepository.saveAndFlush(new Region("CLEANUP-" + expiresAt.getEpochSecond(), "김해시", true));
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "cleanup-" + expiresAt.getEpochSecond() + "@example.com",
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        return imageObjectRepository.saveAndFlush(ImageObject.createUploadCandidate(
            "contents/cleanup-" + expiresAt.getEpochSecond() + ".webp",
            operator,
            region,
            "image/webp",
            1024L,
            CHECKSUM,
            expiresAt
        ));
    }

    private static class FakeImageStorageClient implements ImageStorageGateway {

        private final List<String> deletedObjectKeys = new ArrayList<>();
        private boolean failNextDelete;

        private void failNextDelete() {
            failNextDelete = true;
        }

        @Override
        public PresignedUpload createPresignedPutUpload(
            String objectKey,
            String mediaType,
            long byteSize,
            String checksum,
            Instant expiresAt
        ) {
            throw new UnsupportedOperationException("createPresignedPutUpload is not used");
        }

        @Override
        public ImageObjectMetadata headObject(String objectKey) {
            throw new UnsupportedOperationException("headObject is not used");
        }

        @Override
        public void deleteObject(String objectKey) {
            deletedObjectKeys.add(objectKey);
            if (failNextDelete) {
                failNextDelete = false;
                throw new IllegalStateException("delete failed");
            }
        }
    }
}
