package io.regionevent.regioneventbackend.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.PresignedUpload;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.StoredObjectMetadata;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@DataJpaTest
@Import({
    RepresentativeImageConnectionService.class,
    RepresentativeImageConnectionServiceTest.TestConfig.class
})
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class RepresentativeImageConnectionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
    private static final long BYTE_SIZE = 1024L;
    private static final String CHECKSUM = "checksum";

    private final RepresentativeImageConnectionService representativeImageConnectionService;
    private final ImageObjectRepository imageObjectRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final FakeImageStorageGateway imageStorageGateway;
    private final EntityManager entityManager;

    @Autowired
    RepresentativeImageConnectionServiceTest(
        RepresentativeImageConnectionService representativeImageConnectionService,
        ImageObjectRepository imageObjectRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        FakeImageStorageGateway imageStorageGateway,
        EntityManager entityManager
    ) {
        this.representativeImageConnectionService = representativeImageConnectionService;
        this.imageObjectRepository = imageObjectRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.imageStorageGateway = imageStorageGateway;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        imageStorageGateway.clear();
    }

    @Test
    void validateAndMarkConnected_whenUploadCandidateIsValid_marksLinkedInCurrentTransaction() {
        Region region = saveRegion("VALID");
        AppUser operator = saveUser("valid-operator");
        ImageObject imageObject = saveUploadCandidate(
            "content/valid.webp",
            operator,
            region,
            NOW.plusSeconds(60)
        );
        entityManager.clear();

        ImageObject connectedImageObject = representativeImageConnectionService.validateAndMarkConnected(
            imageObject.getImageObjectId(),
            operator.getUserId(),
            region.getRegionId()
        );

        assertThat(connectedImageObject.getLinkedAt()).isEqualTo(NOW);
        assertThat(connectedImageObject.getCreatedByUser()).isNull();
    }

    @Test
    void validateAndMarkConnected_whenOperatorIsDifferent_rejectsConnection() {
        Region region = saveRegion("OTHER-OPERATOR");
        AppUser uploader = saveUser("uploader");
        AppUser otherOperator = saveUser("other-operator");
        ImageObject imageObject = saveUploadCandidate(
            "content/other-operator.webp",
            uploader,
            region,
            NOW.plusSeconds(60)
        );
        entityManager.clear();

        assertInvalidInputThrownBy(() -> representativeImageConnectionService.validateAndMarkConnected(
            imageObject.getImageObjectId(),
            otherOperator.getUserId(),
            region.getRegionId()
        ));
    }

    @Test
    void validateAndMarkConnected_whenRegionIsDifferent_rejectsConnection() {
        Region uploadRegion = saveRegion("UPLOAD-REGION");
        Region otherRegion = saveRegion("OTHER-REGION");
        AppUser operator = saveUser("region-operator");
        ImageObject imageObject = saveUploadCandidate(
            "content/other-region.webp",
            operator,
            uploadRegion,
            NOW.plusSeconds(60)
        );
        entityManager.clear();

        assertInvalidInputThrownBy(() -> representativeImageConnectionService.validateAndMarkConnected(
            imageObject.getImageObjectId(),
            operator.getUserId(),
            otherRegion.getRegionId()
        ));
    }

    @Test
    void validateAndMarkConnected_whenUploadCandidateIsExpired_rejectsConnection() {
        Region region = saveRegion("EXPIRED");
        AppUser operator = saveUser("expired-operator");
        ImageObject imageObject = saveUploadCandidate(
            "content/expired.webp",
            operator,
            region,
            NOW
        );
        entityManager.clear();

        assertInvalidInputThrownBy(() -> representativeImageConnectionService.validateAndMarkConnected(
            imageObject.getImageObjectId(),
            operator.getUserId(),
            region.getRegionId()
        ));
    }

    @Test
    void validateAndMarkConnected_whenImageObjectIsAlreadyLinked_rejectsConnection() {
        Region region = saveRegion("ALREADY-LINKED");
        AppUser operator = saveUser("linked-operator");
        ImageObject imageObject = saveUploadCandidate(
            "content/already-linked.webp",
            operator,
            region,
            NOW.plusSeconds(60)
        );
        imageObject.markLinked(NOW.minusSeconds(1));
        imageObjectRepository.saveAndFlush(imageObject);
        entityManager.clear();

        assertInvalidInputThrownBy(() -> representativeImageConnectionService.validateAndMarkConnected(
            imageObject.getImageObjectId(),
            operator.getUserId(),
            region.getRegionId()
        ));
    }

    @Test
    void validateAndMarkConnected_whenImageObjectIsDeletePending_rejectsConnection() {
        Region region = saveRegion("DELETE-PENDING");
        AppUser operator = saveUser("delete-pending-operator");
        ImageObject imageObject = saveUploadCandidate(
            "content/delete-pending.webp",
            operator,
            region,
            NOW.plusSeconds(60)
        );
        imageObject.markDeletePending();
        imageObjectRepository.saveAndFlush(imageObject);
        entityManager.clear();

        assertInvalidInputThrownBy(() -> representativeImageConnectionService.validateAndMarkConnected(
            imageObject.getImageObjectId(),
            operator.getUserId(),
            region.getRegionId()
        ));
    }

    @Test
    void validateAndMarkConnected_whenStoredByteSizeIsDifferent_rejectsConnection() {
        assertStoredMetadataMismatchRejected(
            "byte-size-mismatch",
            BYTE_SIZE + 1,
            CHECKSUM
        );
    }

    @Test
    void validateAndMarkConnected_whenStoredChecksumIsDifferent_rejectsConnection() {
        assertStoredMetadataMismatchRejected(
            "checksum-mismatch",
            BYTE_SIZE,
            "other-checksum"
        );
    }

    private void assertStoredMetadataMismatchRejected(
        String scenario,
        long storedByteSize,
        String storedChecksum
    ) {
        Region region = saveRegion(scenario.toUpperCase());
        AppUser operator = saveUser(scenario + "-operator");
        ImageObject imageObject = saveUploadCandidate(
            "content/" + scenario + ".webp",
            operator,
            region,
            NOW.plusSeconds(60)
        );
        imageStorageGateway.putMetadata(imageObject.getObjectKey(), storedByteSize, storedChecksum);
        entityManager.clear();

        assertInvalidInputThrownBy(() -> representativeImageConnectionService.validateAndMarkConnected(
            imageObject.getImageObjectId(),
            operator.getUserId(),
            region.getRegionId()
        ));
    }

    private Region saveRegion(String codePrefix) {
        return regionRepository.saveAndFlush(new Region(
            codePrefix,
            "Region",
            true
        ));
    }

    private AppUser saveUser(String loginPrefix) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginPrefix + "@example.com",
            "hashed-password",
            "Operator",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private ImageObject saveUploadCandidate(
        String objectKey,
        AppUser operator,
        Region region,
        Instant uploadExpiresAt
    ) {
        imageStorageGateway.putMetadata(objectKey, BYTE_SIZE, CHECKSUM);
        return imageObjectRepository.saveAndFlush(ImageObject.createUploadCandidate(
            objectKey,
            operator,
            region,
            "image/webp",
            BYTE_SIZE,
            CHECKSUM,
            uploadExpiresAt
        ));
    }

    private void assertInvalidInputThrownBy(ThrowingCallable throwingCallable) {
        assertThatThrownBy(throwingCallable)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        FakeImageStorageGateway imageStorageGateway() {
            return new FakeImageStorageGateway();
        }
    }

    static class FakeImageStorageGateway implements ImageStorageGateway {

        private final Map<String, StoredObjectMetadata> metadataByObjectKey = new HashMap<>();

        @Override
        public PresignedUpload createPresignedPutUpload(
            String objectKey,
            String mediaType,
            long byteSize,
            String checksum
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredObjectMetadata findMetadata(String objectKey) {
            return metadataByObjectKey.get(objectKey);
        }

        @Override
        public void delete(String objectKey) {
            throw new UnsupportedOperationException();
        }

        void putMetadata(String objectKey, long byteSize, String checksum) {
            metadataByObjectKey.put(objectKey, new StoredObjectMetadata(byteSize, checksum));
        }

        void clear() {
            metadataByObjectKey.clear();
        }
    }
}
