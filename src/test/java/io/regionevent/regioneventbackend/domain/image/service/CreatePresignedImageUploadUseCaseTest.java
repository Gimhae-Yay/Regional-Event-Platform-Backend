package io.regionevent.regioneventbackend.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorityService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class CreatePresignedImageUploadUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");
    private static final String CHECKSUM = Base64.getEncoder().encodeToString(new byte[32]);

    private final CreatePresignedImageUploadUseCase useCase;
    private final ImageObjectRepository imageObjectRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Autowired
    CreatePresignedImageUploadUseCaseTest(
        ImageObjectRepository imageObjectRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository
    ) {
        this.imageObjectRepository = imageObjectRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        PresignedImageUploadService presignedImageUploadService = new PresignedImageUploadService(
            imageObjectRepository,
            new ImageObjectKeyGenerator(),
            new FakeImageStorageClient(),
            new PresignedImageUploadSettings(Duration.ofMinutes(10)),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        this.useCase = new CreatePresignedImageUploadUseCase(
            new OperatorAuthorityService(userRoleAssignmentRepository),
            presignedImageUploadService
        );
    }

    @Test
    void createUpload_whenOperatorIsValid_persistsUploadCandidateAndReturnsPresignedUrl() {
        AppUser operator = saveOperator("operator@example.com", true);
        PresignedImageUploadCommand command = new PresignedImageUploadCommand(
            "image/webp",
            1024,
            CHECKSUM,
            "CONTENT_REPRESENTATIVE"
        );

        PresignedImageUploadResult response = useCase.createUpload(operator.getUserId(), command);

        ImageObject imageObject = imageObjectRepository.findById(Long.valueOf(response.imageObjectId()))
            .orElseThrow();
        assertThat(response.uploadUrl()).startsWith("https://storage.example/");
        assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(response.uploadHeaders())
            .containsEntry("Content-Type", "image/webp")
            .containsEntry("Content-Length", "1024")
            .containsEntry("x-amz-checksum-sha256", CHECKSUM);
        assertThat(imageObject.getCreatedByUser().getUserId()).isEqualTo(operator.getUserId());
        assertThat(imageObject.getRegion().getRegionId()).isNotNull();
        assertThat(imageObject.getUploadExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(imageObject.getLinkedAt()).isNull();
        assertThat(imageObject.getLifecycleStatus()).isEqualTo(ImageLifecycleStatus.ACTIVE);
        assertThat(imageObject.getObjectKey()).doesNotContain(operator.getUserId().toString());
    }

    @Test
    void createUpload_whenChecksumIsNotSha256Base64_rejectsRequest() {
        AppUser operator = saveOperator("invalid-checksum@example.com", true);
        PresignedImageUploadCommand command = new PresignedImageUploadCommand(
            "image/webp",
            1024,
            "sha256:test",
            "CONTENT_REPRESENTATIVE"
        );

        assertThatThrownBy(() -> useCase.createUpload(operator.getUserId(), command))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );
    }

    @Test
    void createUpload_whenOperatorRoleIsMissing_rejectsRequest() {
        AppUser visitor = saveOperator("visitor-only@example.com", false);
        PresignedImageUploadCommand command = new PresignedImageUploadCommand(
            "image/webp",
            1024,
            CHECKSUM,
            "CONTENT_REPRESENTATIVE"
        );

        assertThatThrownBy(() -> useCase.createUpload(visitor.getUserId(), command))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
    }

    private AppUser saveOperator(String loginIdentifier, boolean assignOperatorRole) {
        Region region = regionRepository.saveAndFlush(new Region("REGION-" + loginIdentifier, "김해시", true));
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        if (assignOperatorRole) {
            userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        }
        return operator;
    }

    private static class FakeImageStorageClient implements ImageStorageGateway {

        @Override
        public PresignedUpload createPresignedPutUpload(
            String objectKey,
            String mediaType,
            long byteSize,
            String checksum,
            Instant expiresAt
        ) {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", mediaType);
            headers.put("Content-Length", Long.toString(byteSize));
            headers.put("x-amz-checksum-sha256", checksum);
            return new PresignedUpload("https://storage.example/" + objectKey, expiresAt, headers);
        }

        @Override
        public ImageObjectMetadata headObject(String objectKey) {
            throw new UnsupportedOperationException("headObject is not used");
        }

        @Override
        public void deleteObject(String objectKey) {
            throw new UnsupportedOperationException("deleteObject is not used");
        }
    }
}
