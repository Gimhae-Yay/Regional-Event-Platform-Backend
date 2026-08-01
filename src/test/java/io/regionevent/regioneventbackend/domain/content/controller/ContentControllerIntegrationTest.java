package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
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
    private AuditEventRepository auditEventRepository;

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

    @Test
    void updateMyContent_whenRejectedContentAndNewRepresentativeImageAreValid_updatesContentAndImage()
        throws Exception {

        AppUser operator = saveUser("update-operator@example.com");
        Region region = saveRegion("UPDATE-CONTENT");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject currentImageObject = saveLinkedImageObject(operator, region, "contents/test/current-update.webp");
        Content content = saveContent(operator, region, currentImageObject, ContentStatus.REJECTED);
        ImageObject replacementImageObject = saveImageObject(operator, region, "contents/test/replacement-update.webp");
        imageStorageGateway.addMetadata(
            replacementImageObject.getObjectKey(),
            replacementImageObject.getByteSize(),
            replacementImageObject.getChecksum()
        );

        mockMvc.perform(put("/api/v1/operator/contents/{contentId}", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(updateRequest(replacementImageObject.getImageObjectId().toString())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 콘텐츠 수정에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(content.getContentId().toString()))
            .andExpect(jsonPath("$.data.status").value("REJECTED"));

        assertThat(contentRepository.findById(content.getContentId()))
            .get()
            .satisfies(updatedContent -> {
                assertThat(updatedContent.getStatus()).isEqualTo(ContentStatus.REJECTED);
                assertThat(updatedContent.getTitle()).isEqualTo("수정된 김해 문화 체험");
                assertThat(updatedContent.getDescription()).isEqualTo("수정된 설명입니다.");
                assertThat(updatedContent.getLocationText()).isEqualTo("김해시 수정로 1");
                assertThat(updatedContent.getOperatingHoursText()).isEqualTo("매주 일요일 11:00~17:00");
                assertThat(updatedContent.getContactText()).isEqualTo("055-111-1111");
                assertThat(updatedContent.getPrecautions()).isEqualTo("수정된 유의사항입니다.");
                assertThat(updatedContent.getAgeRequirement()).isEqualTo("중학생 이상");
                assertThat(updatedContent.getMaterials()).isEqualTo("개인 컵");
                assertThat(updatedContent.getCancellationPolicyText()).isEqualTo("회차 시작 1일 전까지 취소 가능합니다.");
                assertThat(updatedContent.getPublishAt()).isEqualTo(Instant.parse("2026-09-15T00:00:00Z"));
                assertThat(updatedContent.getRepresentativeImageObject().getImageObjectId())
                    .isEqualTo(replacementImageObject.getImageObjectId());
                assertThat(updatedContent.getRepresentativeImageAssignedAt()).isNotNull();
            });
        assertThat(imageObjectRepository.findById(replacementImageObject.getImageObjectId()))
            .get()
            .satisfies(imageObject -> {
                assertThat(imageObject.getCreatedByUser()).isNull();
                assertThat(imageObject.getLinkedAt()).isNotNull();
            });
        assertThat(contentLogRepository.count()).isZero();
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void updateMyContent_whenAuthorizationHeaderIsMissing_returnsUnauthenticatedWithoutUpdatingContent()
        throws Exception {

        AppUser operator = saveUser("update-unauthenticated-operator@example.com");
        Region region = saveRegion("UPDATE-UNAUTHENTICATED");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject currentImageObject = saveLinkedImageObject(
            operator,
            region,
            "contents/test/current-unauthenticated.webp"
        );
        Content content = saveContent(operator, region, currentImageObject, ContentStatus.REJECTED);

        mockMvc.perform(put("/api/v1/operator/contents/{contentId}", content.getContentId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestWithoutRepresentativeImage()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(contentRepository.findById(content.getContentId()))
            .get()
            .satisfies(savedContent -> assertThat(savedContent.getTitle()).isEqualTo("기존 김해 문화 체험"));
    }

    @Test
    void updateMyContent_whenRepresentativeImageObjectIdIsOmitted_keepsExistingRepresentativeImage()
        throws Exception {

        AppUser operator = saveUser("update-omitted-operator@example.com");
        Region region = saveRegion("UPDATE-OMITTED");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject currentImageObject = saveLinkedImageObject(operator, region, "contents/test/current-omitted.webp");
        Content content = saveContent(operator, region, currentImageObject, ContentStatus.REJECTED);

        mockMvc.perform(put("/api/v1/operator/contents/{contentId}", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestWithoutRepresentativeImage()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"));

        assertThat(contentRepository.findById(content.getContentId()))
            .get()
            .satisfies(updatedContent -> assertThat(updatedContent.getRepresentativeImageObject().getImageObjectId())
                .isEqualTo(currentImageObject.getImageObjectId()));
    }

    @Test
    void updateMyContent_whenRepresentativeImageObjectIdIsNull_keepsExistingRepresentativeImage()
        throws Exception {

        AppUser operator = saveUser("update-null-operator@example.com");
        Region region = saveRegion("UPDATE-NULL");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject currentImageObject = saveLinkedImageObject(operator, region, "contents/test/current-null.webp");
        Content content = saveContent(operator, region, currentImageObject, ContentStatus.REJECTED);

        mockMvc.perform(put("/api/v1/operator/contents/{contentId}", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestWithNullRepresentativeImage()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"));

        assertThat(contentRepository.findById(content.getContentId()))
            .get()
            .satisfies(updatedContent -> assertThat(updatedContent.getRepresentativeImageObject().getImageObjectId())
                .isEqualTo(currentImageObject.getImageObjectId()));
    }

    @Test
    void updateMyContent_whenCurrentRepresentativeImageObjectIdIsProvided_keepsExistingRepresentativeImage()
        throws Exception {

        AppUser operator = saveUser("update-same-image-operator@example.com");
        Region region = saveRegion("UPDATE-SAME-IMAGE");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject currentImageObject = saveLinkedImageObject(operator, region, "contents/test/current-same.webp");
        Content content = saveContent(operator, region, currentImageObject, ContentStatus.REJECTED);

        mockMvc.perform(put("/api/v1/operator/contents/{contentId}", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest(currentImageObject.getImageObjectId().toString())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"));

        assertThat(contentRepository.findById(content.getContentId()))
            .get()
            .satisfies(updatedContent -> assertThat(updatedContent.getRepresentativeImageObject().getImageObjectId())
                .isEqualTo(currentImageObject.getImageObjectId()));
    }

    @Test
    void updateMyContent_whenUserIsNotOwner_returnsForbiddenWithoutUpdatingContent() throws Exception {
        AppUser owner = saveUser("update-owner@example.com");
        AppUser otherOperator = saveUser("update-other-operator@example.com");
        Region region = saveRegion("UPDATE-FORBIDDEN");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(owner, UserRole.OPERATOR, region));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(otherOperator, UserRole.OPERATOR, region));
        ImageObject currentImageObject = saveLinkedImageObject(owner, region, "contents/test/current-forbidden.webp");
        Content content = saveContent(owner, region, currentImageObject, ContentStatus.REJECTED);

        mockMvc.perform(put("/api/v1/operator/contents/{contentId}", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(otherOperator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestWithoutRepresentativeImage()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(contentRepository.findById(content.getContentId()))
            .get()
            .satisfies(savedContent -> assertThat(savedContent.getTitle()).isEqualTo("기존 김해 문화 체험"));
    }

    @Test
    void updateMyContent_whenContentDoesNotExist_returnsNotFound() throws Exception {
        AppUser operator = saveUser("update-not-found-operator@example.com");
        Region region = saveRegion("UPDATE-NOT-FOUND");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));

        mockMvc.perform(put("/api/v1/operator/contents/{contentId}", 9_999_999L)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestWithoutRepresentativeImage()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void updateMyContent_whenContentIsSoftDeleted_returnsNotFoundWithoutUpdatingContent() throws Exception {
        AppUser operator = saveUser("update-soft-deleted-operator@example.com");
        Region region = saveRegion("UPDATE-SOFT-DELETED");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject currentImageObject = saveLinkedImageObject(operator, region, "contents/test/current-deleted.webp");
        Content content = saveContent(operator, region, currentImageObject, ContentStatus.PENDING);
        content.softDelete();
        contentRepository.saveAndFlush(content);

        mockMvc.perform(put("/api/v1/operator/contents/{contentId}", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestWithoutRepresentativeImage()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(contentRepository.findById(content.getContentId()))
            .get()
            .satisfies(savedContent -> assertThat(savedContent.getTitle()).isEqualTo("기존 김해 문화 체험"));
    }

    @Test
    void updateMyContent_whenContentIsNotRejected_returnsContentStateConflictWithoutUpdatingContent()
        throws Exception {

        AppUser operator = saveUser("update-pending-operator@example.com");
        Region region = saveRegion("UPDATE-PENDING");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject currentImageObject = saveLinkedImageObject(operator, region, "contents/test/current-pending.webp");
        Content content = saveContent(operator, region, currentImageObject, ContentStatus.PENDING);

        mockMvc.perform(put("/api/v1/operator/contents/{contentId}", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequestWithoutRepresentativeImage()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertThat(contentRepository.findById(content.getContentId()))
            .get()
            .satisfies(savedContent -> assertThat(savedContent.getTitle()).isEqualTo("기존 김해 문화 체험"));
    }

    @Test
    void updateMyContent_whenRepresentativeImageObjectIdIsNotJsonString_returnsInvalidType()
        throws Exception {

        AppUser operator = saveUser("update-type-operator@example.com");
        Region region = saveRegion("UPDATE-TYPE");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject currentImageObject = saveLinkedImageObject(operator, region, "contents/test/current-type.webp");
        Content content = saveContent(operator, region, currentImageObject, ContentStatus.REJECTED);

        mockMvc.perform(put("/api/v1/operator/contents/{contentId}", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(nonStringUpdateRequest("1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void updateMyContent_whenRequiredFieldIsBlank_returnsInvalidInputWithoutUpdatingContent()
        throws Exception {

        AppUser operator = saveUser("update-blank-operator@example.com");
        Region region = saveRegion("UPDATE-BLANK");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject currentImageObject = saveLinkedImageObject(operator, region, "contents/test/current-blank.webp");
        Content content = saveContent(operator, region, currentImageObject, ContentStatus.REJECTED);

        mockMvc.perform(put("/api/v1/operator/contents/{contentId}", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(blankTitleUpdateRequest()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(contentRepository.findById(content.getContentId()))
            .get()
            .satisfies(savedContent -> assertThat(savedContent.getTitle()).isEqualTo("기존 김해 문화 체험"));
    }

    @Test
    void updateMyContent_whenDateTimeOffsetIsNotSeoul_returnsInvalidInputWithoutUpdatingContent()
        throws Exception {

        AppUser operator = saveUser("update-offset-operator@example.com");
        Region region = saveRegion("UPDATE-OFFSET");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject currentImageObject = saveLinkedImageObject(operator, region, "contents/test/current-offset.webp");
        Content content = saveContent(operator, region, currentImageObject, ContentStatus.REJECTED);

        mockMvc.perform(put("/api/v1/operator/contents/{contentId}", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(nonSeoulOffsetUpdateRequest()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(contentRepository.findById(content.getContentId()))
            .get()
            .satisfies(savedContent -> assertThat(savedContent.getTitle()).isEqualTo("기존 김해 문화 체험"));
    }

    @Test
    void updateMyContent_whenReplacementImageMetadataDoesNotMatch_returnsInvalidInputWithoutUpdatingContent()
        throws Exception {

        AppUser operator = saveUser("update-image-fail-operator@example.com");
        Region region = saveRegion("UPDATE-IMAGE-FAIL");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject currentImageObject = saveLinkedImageObject(operator, region, "contents/test/current-image-fail.webp");
        Content content = saveContent(operator, region, currentImageObject, ContentStatus.REJECTED);
        ImageObject replacementImageObject = saveImageObject(operator, region, "contents/test/replacement-image-fail.webp");
        imageStorageGateway.addMetadata(
            replacementImageObject.getObjectKey(),
            1L,
            replacementImageObject.getChecksum()
        );

        mockMvc.perform(put("/api/v1/operator/contents/{contentId}", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest(replacementImageObject.getImageObjectId().toString())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(contentRepository.findById(content.getContentId()))
            .get()
            .satisfies(savedContent -> {
                assertThat(savedContent.getTitle()).isEqualTo("기존 김해 문화 체험");
                assertThat(savedContent.getRepresentativeImageObject().getImageObjectId())
                    .isEqualTo(currentImageObject.getImageObjectId());
            });
        assertThat(imageObjectRepository.findById(replacementImageObject.getImageObjectId()))
            .get()
            .satisfies(imageObject -> {
                assertThat(imageObject.getCreatedByUser().getUserId()).isEqualTo(operator.getUserId());
                assertThat(imageObject.getLinkedAt()).isNull();
            });
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

    private ImageObject saveLinkedImageObject(AppUser operator, Region region, String objectKey) {
        ImageObject imageObject = saveImageObject(operator, region, objectKey);
        imageObject.markLinked(Instant.now());
        return imageObjectRepository.saveAndFlush(imageObject);
    }

    private Content saveContent(
        AppUser operator,
        Region region,
        ImageObject representativeImageObject,
        ContentStatus status
    ) {
        Content content = new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            status,
            "기존 김해 문화 체험",
            "기존 설명입니다.",
            "김해시 기존로 1",
            "매주 토요일 10:00~16:00",
            "055-000-0000",
            "기존 유의사항입니다.",
            "초등학생 이상",
            "필기 도구",
            "회차 시작 전까지 취소 가능합니다.",
            Instant.parse("2026-08-15T00:00:00Z")
        );
        content.assignRepresentativeImage(representativeImageObject, Instant.now());
        return contentRepository.saveAndFlush(content);
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

    private String updateRequest(String representativeImageObjectId) {
        return updateRequestWithRepresentativeImageField(
            ",\n              \"representativeImageObjectId\": \"%s\"".formatted(representativeImageObjectId)
        );
    }

    private String updateRequestWithoutRepresentativeImage() {
        return updateRequestWithRepresentativeImageField("");
    }

    private String updateRequestWithNullRepresentativeImage() {
        return updateRequestWithRepresentativeImageField(",\n              \"representativeImageObjectId\": null");
    }

    private String updateRequestWithRepresentativeImageField(String representativeImageField) {
        return """
            {
              "title": "수정된 김해 문화 체험",
              "description": "수정된 설명입니다.",
              "locationText": "김해시 수정로 1",
              "operatingHoursText": "매주 일요일 11:00~17:00",
              "contactText": "055-111-1111",
              "precautions": "수정된 유의사항입니다.",
              "ageRequirement": "중학생 이상",
              "materials": "개인 컵",
              "cancellationPolicyText": "회차 시작 1일 전까지 취소 가능합니다.",
              "publishAt": "2026-09-15T09:00:00+09:00"%s
            }
            """.formatted(representativeImageField);
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

    private String nonStringUpdateRequest(String representativeImageObjectId) {
        return updateRequestWithRepresentativeImageField(
            ",\n              \"representativeImageObjectId\": %s".formatted(representativeImageObjectId)
        );
    }

    private String blankTitleUpdateRequest() {
        return updateRequestWithoutRepresentativeImage()
            .replace("\"title\": \"수정된 김해 문화 체험\"", "\"title\": \"\"");
    }

    private String nonSeoulOffsetUpdateRequest() {
        return updateRequestWithoutRepresentativeImage()
            .replace("\"publishAt\": \"2026-09-15T09:00:00+09:00\"", "\"publishAt\": \"2026-09-15T00:00:00Z\"");
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
