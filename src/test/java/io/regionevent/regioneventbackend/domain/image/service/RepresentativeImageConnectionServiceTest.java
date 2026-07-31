package io.regionevent.regioneventbackend.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class RepresentativeImageConnectionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");
    private static final String CHECKSUM = Base64.getEncoder().encodeToString(new byte[32]);

    private final RepresentativeImageConnectionService service;
    private final ImageObjectRepository imageObjectRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;
    private final FakeImageStorageClient imageStorageClient;

    @Autowired
    RepresentativeImageConnectionServiceTest(
        ImageObjectRepository imageObjectRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager
    ) {
        this.imageObjectRepository = imageObjectRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
        this.imageStorageClient = new FakeImageStorageClient();
        this.service = new RepresentativeImageConnectionService(
            imageObjectRepository,
            imageStorageClient,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void validateAndMarkConnected_whenObjectMatchesPolicy_marksLinkedAndRemovesUploader() {
        Region region = saveRegion("GIMHAE");
        AppUser operator = saveUser("operator@example.com");
        ImageObject imageObject = saveUploadCandidate(operator, region, NOW.plusSeconds(60));
        imageStorageClient.metadata = new ImageObjectMetadata(CHECKSUM, 1024L);

        service.validateAndMarkConnected(
            imageObject.getImageObjectId(),
            operator.getUserId(),
            region.getRegionId()
        );
        imageObjectRepository.flush();
        entityManager.clear();

        ImageObject foundImageObject = imageObjectRepository.findById(imageObject.getImageObjectId()).orElseThrow();
        assertThat(foundImageObject.getLinkedAt()).isEqualTo(NOW);
        assertThat(foundImageObject.getCreatedByUser()).isNull();
    }

    @Test
    void validateAndMarkConnected_whenS3MetadataDiffers_rejectsConnection() {
        Region region = saveRegion("DONGHAE");
        AppUser operator = saveUser("metadata-mismatch@example.com");
        ImageObject imageObject = saveUploadCandidate(operator, region, NOW.plusSeconds(60));
        imageStorageClient.metadata = new ImageObjectMetadata(CHECKSUM, 2048L);

        assertThatThrownBy(() -> service.validateAndMarkConnected(
            imageObject.getImageObjectId(),
            operator.getUserId(),
            region.getRegionId()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
        );
    }

    @Test
    void validateAndMarkConnected_whenObjectIsExpired_rejectsConnection() {
        Region region = saveRegion("BUSAN");
        AppUser operator = saveUser("expired@example.com");
        ImageObject imageObject = saveUploadCandidate(operator, region, NOW.minusSeconds(1));
        imageStorageClient.metadata = new ImageObjectMetadata(CHECKSUM, 1024L);

        assertThatThrownBy(() -> service.validateAndMarkConnected(
            imageObject.getImageObjectId(),
            operator.getUserId(),
            region.getRegionId()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
        );
    }

    private ImageObject saveUploadCandidate(AppUser operator, Region region, Instant expiresAt) {
        return imageObjectRepository.saveAndFlush(ImageObject.createUploadCandidate(
            "contents/test-" + operator.getLoginIdentifier() + ".webp",
            operator,
            region,
            "image/webp",
            1024L,
            CHECKSUM,
            expiresAt
        ));
    }

    private Region saveRegion(String code) {
        return regionRepository.saveAndFlush(new Region(code, code + " 지역", true));
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private static class FakeImageStorageClient implements ImageStorageGateway {

        private ImageObjectMetadata metadata;

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
            return metadata;
        }

        @Override
        public void deleteObject(String objectKey) {
            throw new UnsupportedOperationException("deleteObject is not used");
        }
    }
}
