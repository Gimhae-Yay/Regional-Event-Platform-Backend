package io.regionevent.regioneventbackend.domain.image.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageException;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.PresignedUpload;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.PresignedViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.StoredObjectMetadata;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Import(UploadRepresentativeImageControllerIntegrationTest.TestImageStorageConfig.class)
@Transactional
class UploadRepresentativeImageControllerIntegrationTest {

    private static final String CHECKSUM = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final Instant EXPIRES_AT = Instant.parse("2026-07-30T05:10:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Autowired
    private ImageObjectRepository imageObjectRepository;

    @Autowired
    private FakeImageStorageGateway imageStorageGateway;

    @BeforeEach
    void setUp() {
        imageStorageGateway.reset();
    }

    @Test
    void createPresignedUrl_whenApprovedOperatorRequestsValidImage_returnsUploadInformation() throws Exception {
        AppUser operator = saveUser("operator@example.com");
        Region region = saveRegion("GIMHAE");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));

        mockMvc.perform(post("/api/v1/operator/uploads/presigned-url")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.imageObjectId").isString())
            .andExpect(jsonPath("$.data.uploadUrl").value("https://example.com/upload"))
            .andExpect(jsonPath("$.data.expiresAt").value("2026-07-30T05:10:00Z"))
            .andExpect(jsonPath("$.data.uploadHeaders.Content-Type").value("image/webp"))
            .andExpect(jsonPath("$.data.uploadHeaders.Content-Length").value("524288"))
            .andExpect(jsonPath("$.data.uploadHeaders.x-amz-checksum-sha256").value(CHECKSUM))
            .andExpect(jsonPath("$.data.objectKey").doesNotExist());

        assertThat(imageStorageGateway.objectKey()).startsWith("contents/");
        assertThat(imageStorageGateway.mediaType()).isEqualTo("image/webp");
        assertThat(imageStorageGateway.byteSize()).isEqualTo(524_288L);
        assertThat(imageStorageGateway.checksum()).isEqualTo(CHECKSUM);
        assertThat(imageObjectRepository.findAll())
            .singleElement()
            .satisfies(imageObject -> {
                assertThat(imageObject.getObjectKey()).isEqualTo(imageStorageGateway.objectKey());
                assertThat(imageObject.getCreatedByUser().getUserId()).isEqualTo(operator.getUserId());
                assertThat(imageObject.getRegion().getRegionId()).isEqualTo(region.getRegionId());
                assertThat(imageObject.getMediaType()).isEqualTo("image/webp");
                assertThat(imageObject.getByteSize()).isEqualTo(524_288L);
                assertThat(imageObject.getChecksum()).isEqualTo(CHECKSUM);
                assertThat(imageObject.getUploadExpiresAt()).isEqualTo(EXPIRES_AT);
                assertThat(imageObject.getLinkedAt()).isNull();
                assertThat(imageObject.getLifecycleStatus()).isEqualTo(ImageLifecycleStatus.ACTIVE);
                assertThat(imageObject.getDeleteAttemptCount()).isZero();
            });
    }

    @Test
    void createPresignedUrl_whenAuthorizationHeaderIsMissing_returnsUnauthenticatedWithoutCreatingImageObject()
        throws Exception {

        mockMvc.perform(post("/api/v1/operator/uploads/presigned-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(imageObjectRepository.count()).isZero();
        assertThat(imageStorageGateway.objectKey()).isNull();
    }

    @Test
    void createPresignedUrl_whenUserIsNotOperator_returnsForbiddenWithoutCreatingImageObject() throws Exception {
        AppUser visitor = saveUser("visitor@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(visitor, UserRole.VISITOR, null));

        mockMvc.perform(post("/api/v1/operator/uploads/presigned-url")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(visitor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(imageObjectRepository.count()).isZero();
        assertThat(imageStorageGateway.objectKey()).isNull();
    }

    @Test
    void createPresignedUrl_whenChecksumIsInvalid_returnsInvalidInputWithoutCreatingImageObject() throws Exception {
        AppUser operator = saveUser("invalid-input-operator@example.com");
        Region region = saveRegion("INVALID-INPUT");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));

        mockMvc.perform(post("/api/v1/operator/uploads/presigned-url")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mediaType": "image/webp",
                      "byteSize": 524288,
                      "checksum": "invalid",
                      "usage": "CONTENT_REPRESENTATIVE"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(imageObjectRepository.count()).isZero();
        assertThat(imageStorageGateway.objectKey()).isNull();
    }

    @Test
    void createPresignedUrl_whenByteSizeTypeIsInvalid_returnsInvalidTypeWithoutCreatingImageObject() throws Exception {
        AppUser operator = saveUser("invalid-type-operator@example.com");
        Region region = saveRegion("INVALID-TYPE");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));

        mockMvc.perform(post("/api/v1/operator/uploads/presigned-url")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mediaType": "image/webp",
                      "byteSize": "not-a-number",
                      "checksum": "%s",
                      "usage": "CONTENT_REPRESENTATIVE"
                    }
                    """.formatted(CHECKSUM)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        assertThat(imageObjectRepository.count()).isZero();
        assertThat(imageStorageGateway.objectKey()).isNull();
    }

    @Test
    void createPresignedUrl_whenStorageFails_returnsInternalServerErrorWithoutCreatingImageObject() throws Exception {
        AppUser operator = saveUser("storage-failure-operator@example.com");
        Region region = saveRegion("STORAGE-FAILURE");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        imageStorageGateway.failNext();

        mockMvc.perform(post("/api/v1/operator/uploads/presigned-url")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        assertThat(imageObjectRepository.count()).isZero();
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "Operator",
            "01012345678",
            AppUserStatus.ACTIVE
        ));
    }

    private Region saveRegion(String regionCode) {
        return regionRepository.saveAndFlush(new Region(regionCode, regionCode, true));
    }

    private String validRequest() {
        return """
            {
              "mediaType": "image/webp",
              "byteSize": 524288,
              "checksum": "%s",
              "usage": "CONTENT_REPRESENTATIVE"
            }
            """.formatted(CHECKSUM);
    }

    @TestConfiguration
    static class TestImageStorageConfig {

        @Bean
        @Primary
        FakeImageStorageGateway fakeImageStorageGateway() {
            return new FakeImageStorageGateway();
        }
    }

    static class FakeImageStorageGateway implements ImageStorageGateway {

        private boolean shouldFail;
        private String objectKey;
        private String mediaType;
        private long byteSize;
        private String checksum;

        @Override
        public PresignedUpload createPresignedPutUpload(
            String objectKey,
            String mediaType,
            long byteSize,
            String checksum
        ) {
            if (shouldFail) {
                throw new ImageStorageException("storage failure");
            }
            this.objectKey = objectKey;
            this.mediaType = mediaType;
            this.byteSize = byteSize;
            this.checksum = checksum;

            Map<String, String> uploadHeaders = new LinkedHashMap<>();
            uploadHeaders.put("Content-Type", mediaType);
            uploadHeaders.put("Content-Length", Long.toString(byteSize));
            uploadHeaders.put("x-amz-checksum-sha256", checksum);
            return new PresignedUpload("https://example.com/upload", EXPIRES_AT, uploadHeaders);
        }

        @Override
        public StoredObjectMetadata findMetadata(String objectKey) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public PresignedViewUrl createPresignedGetUrl(String objectKey) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void delete(String objectKey) {
            throw new UnsupportedOperationException("not used");
        }

        void failNext() {
            shouldFail = true;
        }

        void reset() {
            shouldFail = false;
            objectKey = null;
            mediaType = null;
            byteSize = 0L;
            checksum = null;
        }

        String objectKey() {
            return objectKey;
        }

        String mediaType() {
            return mediaType;
        }

        long byteSize() {
            return byteSize;
        }

        String checksum() {
            return checksum;
        }
    }
}
