package io.regionevent.regioneventbackend.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.EntityManager;

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
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.PresignedViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.StoredObjectMetadata;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

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
        public PresignedViewUrl createPresignedGetUrl(String objectKey) {
            throw new UnsupportedOperationException();
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
