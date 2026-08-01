package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
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

import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
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
@Import(ContentControllerIntegrationTest.TestImageStorageConfig.class)
@Transactional
class ContentControllerIntegrationTest {

    private static final String CHECKSUM = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

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
    private ContentRepository contentRepository;

    @Autowired
    private ContentSessionRepository contentSessionRepository;

    @Autowired
    private ContentLogRepository contentLogRepository;

    @Autowired
    private FakeImageStorageGateway imageStorageGateway;

    @BeforeEach
    void setUp() {
        imageStorageGateway.reset();
    }

    @Test
    void createContent_whenApprovedOperatorRequestsValidContent_createsPendingContentWithSessionsAndLog()
        throws Exception {

        AppUser operator = saveUser("content-operator@example.com");
        Region region = saveRegion("CREATE-CONTENT");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject imageObject = saveImageObject(operator, region, "contents/test/representative.webp");
        imageStorageGateway.addMetadata(imageObject.getObjectKey(), imageObject.getByteSize(), imageObject.getChecksum());

        mockMvc.perform(post("/api/v1/operator/contents")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(validRequest(imageObject.getImageObjectId().toString())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 생성과 승인 요청에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").isString())
            .andExpect(jsonPath("$.data.contentType").value("EVENT_EXPERIENCE"))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.submittedAt").isString());

        assertThat(contentRepository.findAll())
            .singleElement()
            .satisfies(content -> {
                assertThat(content.getRegion().getRegionId()).isEqualTo(region.getRegionId());
                assertThat(content.getOperator().getUserId()).isEqualTo(operator.getUserId());
                assertThat(content.getContentType()).isEqualTo(ContentType.EVENT_EXPERIENCE);
                assertThat(content.getStatus()).isEqualTo(ContentStatus.PENDING);
                assertThat(content.getTitle()).isEqualTo("김해 가야문화 체험");
                assertThat(content.getRepresentativeImageObject().getImageObjectId())
                    .isEqualTo(imageObject.getImageObjectId());
                assertThat(content.getRepresentativeImageAssignedAt()).isNotNull();
            });
        assertThat(contentSessionRepository.findAll())
            .singleElement()
            .satisfies(session -> {
                assertThat(session.getStatus()).isEqualTo(ContentSessionStatus.PENDING);
                assertThat(session.getCapacity()).isEqualTo(20);
                assertThat(session.getRemainingCapacity()).isEqualTo(20);
            });
        assertThat(contentLogRepository.findAll())
            .singleElement()
            .satisfies(contentLog -> {
                assertThat(contentLog.getActor().getUserId()).isEqualTo(operator.getUserId());
                assertThat(contentLog.getStatus()).isEqualTo(ContentLogStatus.PENDING);
                assertThat(contentLog.getReason()).isNull();
            });
        assertThat(imageObjectRepository.findById(imageObject.getImageObjectId()))
            .get()
            .satisfies(linkedImageObject -> {
                assertThat(linkedImageObject.getCreatedByUser()).isNull();
                assertThat(linkedImageObject.getLinkedAt()).isNotNull();
                assertThat(linkedImageObject.getLifecycleStatus()).isEqualTo(ImageLifecycleStatus.ACTIVE);
            });
    }

    @Test
    void createContent_whenAuthorizationHeaderIsMissing_returnsUnauthenticatedWithoutCreatingContent()
        throws Exception {

        mockMvc.perform(post("/api/v1/operator/contents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest("1")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(contentRepository.count()).isZero();
    }

    @Test
    void createContent_whenUserIsNotOperator_returnsForbiddenWithoutCreatingContent() throws Exception {
        AppUser visitor = saveUser("content-visitor@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(visitor, UserRole.VISITOR, null));

        mockMvc.perform(post("/api/v1/operator/contents")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(visitor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest("1")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(contentRepository.count()).isZero();
    }

    @Test
    void createContent_whenSessionTimeRangeIsInvalid_returnsInvalidInputWithoutCreatingContent()
        throws Exception {

        AppUser operator = saveUser("invalid-session-operator@example.com");
        Region region = saveRegion("INVALID-SESSION");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject imageObject = saveImageObject(operator, region, "contents/test/invalid-session.webp");
        imageStorageGateway.addMetadata(imageObject.getObjectKey(), imageObject.getByteSize(), imageObject.getChecksum());

        mockMvc.perform(post("/api/v1/operator/contents")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidSessionRequest(imageObject.getImageObjectId().toString())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(contentRepository.count()).isZero();
        assertThat(imageObjectRepository.findById(imageObject.getImageObjectId()))
            .get()
            .satisfies(candidate -> {
                assertThat(candidate.getCreatedByUser().getUserId()).isEqualTo(operator.getUserId());
                assertThat(candidate.getLinkedAt()).isNull();
            });
    }

    @Test
    void createContent_whenRepresentativeImageObjectIdIsNotJsonString_returnsInvalidTypeWithoutCreatingContent()
        throws Exception {

        AppUser operator = saveUser("image-id-type-operator@example.com");
        Region region = saveRegion("IMAGE-ID-TYPE");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject imageObject = saveImageObject(operator, region, "contents/test/image-id-type.webp");
        imageStorageGateway.addMetadata(imageObject.getObjectKey(), imageObject.getByteSize(), imageObject.getChecksum());

        mockMvc.perform(post("/api/v1/operator/contents")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(nonStringImageObjectIdRequest(imageObject.getImageObjectId().toString())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        assertThat(contentRepository.count()).isZero();
    }

    @Test
    void createContent_whenCapacityTypeIsInvalid_returnsInvalidTypeWithoutCreatingContent()
        throws Exception {

        AppUser operator = saveUser("capacity-type-operator@example.com");
        Region region = saveRegion("CAPACITY-TYPE");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject imageObject = saveImageObject(operator, region, "contents/test/capacity-type.webp");
        imageStorageGateway.addMetadata(imageObject.getObjectKey(), imageObject.getByteSize(), imageObject.getChecksum());

        mockMvc.perform(post("/api/v1/operator/contents")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidCapacityTypeRequest(imageObject.getImageObjectId().toString())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        assertThat(contentRepository.count()).isZero();
    }

    @Test
    void createContent_whenDateTimeOffsetIsNotSeoul_returnsInvalidInputWithoutCreatingContent()
        throws Exception {

        AppUser operator = saveUser("offset-operator@example.com");
        Region region = saveRegion("OFFSET");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject imageObject = saveImageObject(operator, region, "contents/test/offset.webp");
        imageStorageGateway.addMetadata(imageObject.getObjectKey(), imageObject.getByteSize(), imageObject.getChecksum());

        mockMvc.perform(post("/api/v1/operator/contents")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(nonSeoulOffsetRequest(imageObject.getImageObjectId().toString())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(contentRepository.count()).isZero();
    }

    @Test
    void createContent_whenRepresentativeImageMetadataDoesNotMatch_returnsInvalidInputWithoutCreatingContent()
        throws Exception {

        AppUser operator = saveUser("invalid-image-operator@example.com");
        Region region = saveRegion("INVALID-IMAGE");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject imageObject = saveImageObject(operator, region, "contents/test/invalid-image.webp");
        imageStorageGateway.addMetadata(imageObject.getObjectKey(), 1L, imageObject.getChecksum());

        mockMvc.perform(post("/api/v1/operator/contents")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest(imageObject.getImageObjectId().toString())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(contentRepository.count()).isZero();
        assertThat(contentSessionRepository.count()).isZero();
        assertThat(contentLogRepository.count()).isZero();
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

    private ImageObject saveImageObject(AppUser operator, Region region, String objectKey) {
        return imageObjectRepository.saveAndFlush(ImageObject.createUploadCandidate(
            objectKey,
            operator,
            region,
            "image/webp",
            524_288L,
            CHECKSUM,
            Instant.now().plusSeconds(86_400)
        ));
    }

    private String validRequest(String representativeImageObjectId) {
        return """
            {
              "title": "김해 가야문화 체험",
              "description": "가야 문화를 체험하는 행사입니다.",
              "locationText": "김해시 가야의길 190",
              "operatingHoursText": "매주 토요일 10:00~16:00",
              "contactText": "055-000-0000",
              "precautions": "편한 복장으로 참여해 주세요.",
              "ageRequirement": "초등학생 이상",
              "materials": "필기 도구",
              "cancellationPolicyText": "회차 시작 전까지 예약 취소가 가능합니다.",
              "publishAt": "2026-08-15T09:00:00+09:00",
              "representativeImageObjectId": "%s",
              "sessions": [
                {
                  "startsAt": "2026-08-16T10:00:00+09:00",
                  "endsAt": "2026-08-16T12:00:00+09:00",
                  "checkinOpenAt": "2026-08-16T09:30:00+09:00",
                  "checkinCloseAt": "2026-08-16T10:30:00+09:00",
                  "capacity": 20
                }
              ]
            }
            """.formatted(representativeImageObjectId);
    }

    private String invalidSessionRequest(String representativeImageObjectId) {
        return validRequest(representativeImageObjectId)
            .replace("\"endsAt\": \"2026-08-16T12:00:00+09:00\"", "\"endsAt\": \"2026-08-16T10:00:00+09:00\"");
    }

    private String nonStringImageObjectIdRequest(String representativeImageObjectId) {
        return validRequest(representativeImageObjectId)
            .replace(
                "\"representativeImageObjectId\": \"%s\"".formatted(representativeImageObjectId),
                "\"representativeImageObjectId\": %s".formatted(representativeImageObjectId)
            );
    }

    private String invalidCapacityTypeRequest(String representativeImageObjectId) {
        return validRequest(representativeImageObjectId)
            .replace("\"capacity\": 20", "\"capacity\": \"not-a-number\"");
    }

    private String nonSeoulOffsetRequest(String representativeImageObjectId) {
        return validRequest(representativeImageObjectId)
            .replace("\"publishAt\": \"2026-08-15T09:00:00+09:00\"", "\"publishAt\": \"2026-08-15T00:00:00Z\"");
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

        private final Map<String, StoredObjectMetadata> metadataByObjectKey = new HashMap<>();

        @Override
        public PresignedUpload createPresignedPutUpload(
            String objectKey,
            String mediaType,
            long byteSize,
            String checksum
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public StoredObjectMetadata findMetadata(String objectKey) {
            return metadataByObjectKey.get(objectKey);
        }

        @Override
        public PresignedViewUrl createPresignedGetUrl(String objectKey) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void delete(String objectKey) {
            throw new UnsupportedOperationException("not used");
        }

        void addMetadata(String objectKey, long byteSize, String checksum) {
            metadataByObjectKey.put(objectKey, new StoredObjectMetadata(byteSize, checksum));
        }

        void reset() {
            metadataByObjectKey.clear();
        }
    }
}
