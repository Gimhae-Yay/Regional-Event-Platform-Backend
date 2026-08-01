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

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
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
    private ContentRevisionRepository contentRevisionRepository;

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
    void createContentRevision_whenPublishedContentRequestsNewImage_createsRevisionWithoutChangingOriginal()
        throws Exception {

        AppUser operator = saveUser("published-revision-operator@example.com");
        Region region = saveRegion("PUBLISHED-REVISION");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject originalImageObject = saveLinkedImageObject(operator, region, "contents/test/published-original.webp");
        Content content = saveContent(operator, region, ContentStatus.PUBLISHED, originalImageObject);
        ImageObject candidateImageObject = saveImageObject(operator, region, "contents/test/published-candidate.webp");
        imageStorageGateway.addMetadata(
            candidateImageObject.getObjectKey(),
            candidateImageObject.getByteSize(),
            candidateImageObject.getChecksum()
        );

        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/revisions", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(revisionRequestWithoutPublishAt(candidateImageObject.getImageObjectId().toString())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.revisionId").isString())
            .andExpect(jsonPath("$.data.contentId").value(content.getContentId().toString()))
            .andExpect(jsonPath("$.data.status").value("EDIT_REQUESTED"))
            .andExpect(jsonPath("$.data.baseContentVersion").value(content.getVersionNo()))
            .andExpect(jsonPath("$.data.submittedAt").isString());

        assertThat(contentRevisionRepository.findAll())
            .singleElement()
            .satisfies(revision -> {
                assertThat(revision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REQUESTED);
                assertThat(revision.getPublishAt()).isNull();
                assertThat(revision.getCandidateImageObject().getImageObjectId())
                    .isEqualTo(candidateImageObject.getImageObjectId());
                assertThat(revision.getCandidateImageAssignedAt()).isNotNull();
            });
        assertThat(contentRepository.findById(content.getContentId()))
            .get()
            .satisfies(updatedContent -> {
                assertThat(updatedContent.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
                assertThat(updatedContent.getRepresentativeImageObject().getImageObjectId())
                    .isEqualTo(originalImageObject.getImageObjectId());
            });
        assertThat(imageObjectRepository.findById(candidateImageObject.getImageObjectId()))
            .get()
            .satisfies(linkedImageObject -> {
                assertThat(linkedImageObject.getCreatedByUser()).isNull();
                assertThat(linkedImageObject.getLinkedAt()).isNotNull();
            });
    }

    @Test
    void createContentRevision_whenRepresentativeImageObjectIdIsOmitted_snapshotsCurrentImage()
        throws Exception {

        AppUser operator = saveUser("revision-no-image-operator@example.com");
        Region region = saveRegion("REVISION-NO-IMAGE");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject originalImageObject = saveLinkedImageObject(operator, region, "contents/test/no-image-original.webp");
        Content content = saveContent(operator, region, ContentStatus.PUBLISHED, originalImageObject);

        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/revisions", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(revisionRequestWithoutImageAndPublishAt()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("EDIT_REQUESTED"));

        assertThat(contentRevisionRepository.findAll())
            .singleElement()
            .satisfies(revision -> {
                assertThat(revision.getCandidateImageObject().getImageObjectId())
                    .isEqualTo(originalImageObject.getImageObjectId());
                assertThat(revision.getCandidateImageAssignedAt())
                    .isEqualTo(content.getRepresentativeImageAssignedAt());
            });
    }

    @Test
    void createContentRevision_whenApprovedContentRequestsRevision_changesContentToPendingAndRecordsLogAndAudit()
        throws Exception {

        AppUser operator = saveUser("approved-revision-operator@example.com");
        Region region = saveRegion("APPROVED-REVISION");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject originalImageObject = saveLinkedImageObject(operator, region, "contents/test/approved-original.webp");
        Content content = saveContent(operator, region, ContentStatus.APPROVED, originalImageObject);
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            operator,
            ContentLogStatus.APPROVED,
            null,
            Instant.now().minusSeconds(60)
        ));

        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/revisions", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(revisionRequestWithoutImage()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("EDIT_REQUESTED"));

        assertThat(contentRepository.findById(content.getContentId()))
            .get()
            .satisfies(updatedContent -> assertThat(updatedContent.getStatus()).isEqualTo(ContentStatus.PENDING));
        assertThat(contentRevisionRepository.findAll())
            .singleElement()
            .satisfies(revision -> assertThat(revision.getPublishAt())
                .isEqualTo(Instant.parse("2026-08-20T00:00:00Z")));
        assertThat(contentLogRepository.findAll())
            .extracting(contentLog -> contentLog.getStatus())
            .contains(ContentLogStatus.APPROVED, ContentLogStatus.PENDING);
        assertThat(auditEventRepository.findAll())
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT);
                assertThat(auditEvent.getTargetId()).isEqualTo(content.getContentId());
                assertThat(auditEvent.getPreviousState()).isEqualTo("APPROVED");
                assertThat(auditEvent.getNextState()).isEqualTo("PENDING");
                assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            });
    }

    @Test
    void createContentRevision_whenSupplementPendingContentRequestsRevision_createsRevision()
        throws Exception {

        AppUser operator = saveUser("supplement-pending-operator@example.com");
        Region region = saveRegion("SUPPLEMENT-PENDING");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject originalImageObject = saveLinkedImageObject(operator, region, "contents/test/supplement-pending.webp");
        Content content = saveContent(operator, region, ContentStatus.PENDING, originalImageObject);
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            operator,
            ContentLogStatus.APPROVED,
            null,
            Instant.now().minusSeconds(120)
        ));
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            operator,
            ContentLogStatus.PENDING,
            null,
            Instant.now().minusSeconds(60)
        ));

        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/revisions", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(revisionRequestWithoutImage()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("EDIT_REQUESTED"));

        assertThat(contentRevisionRepository.findAll())
            .singleElement()
            .satisfies(revision -> {
                assertThat(revision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REQUESTED);
                assertThat(revision.getPublishAt()).isEqualTo(Instant.parse("2026-08-20T00:00:00Z"));
            });
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void createContentRevision_whenPublishedContentContainsPublishAt_returnsContentStateConflict()
        throws Exception {

        AppUser operator = saveUser("published-publish-at-operator@example.com");
        Region region = saveRegion("PUBLISHED-PUBLISH-AT");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject originalImageObject = saveLinkedImageObject(operator, region, "contents/test/published-publish-at.webp");
        Content content = saveContent(operator, region, ContentStatus.PUBLISHED, originalImageObject);

        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/revisions", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(revisionRequestWithoutImage()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertThat(contentRevisionRepository.count()).isZero();
    }

    @Test
    void createContentRevision_whenApprovedContentOmitsPublishAt_returnsContentStateConflict()
        throws Exception {

        AppUser operator = saveUser("approved-no-publish-at-operator@example.com");
        Region region = saveRegion("APPROVED-NO-PUBLISH-AT");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject originalImageObject = saveLinkedImageObject(operator, region, "contents/test/approved-no-publish-at.webp");
        Content content = saveContent(operator, region, ContentStatus.APPROVED, originalImageObject);

        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/revisions", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(revisionRequestWithoutImageAndPublishAt()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertThat(contentRevisionRepository.count()).isZero();
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void createContentRevision_whenInitialPendingContentRequestsRevision_returnsContentStateConflict()
        throws Exception {

        AppUser operator = saveUser("pending-revision-operator@example.com");
        Region region = saveRegion("PENDING-REVISION");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject originalImageObject = saveLinkedImageObject(operator, region, "contents/test/pending-original.webp");
        Content content = saveContent(operator, region, ContentStatus.PENDING, originalImageObject);
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            operator,
            ContentLogStatus.PENDING,
            null,
            Instant.now().minusSeconds(60)
        ));

        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/revisions", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(revisionRequestWithoutImage()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertThat(contentRevisionRepository.count()).isZero();
    }

    @Test
    void createContentRevision_whenActiveRevisionExists_returnsContentStateConflict()
        throws Exception {

        AppUser operator = saveUser("active-revision-operator@example.com");
        Region region = saveRegion("ACTIVE-REVISION");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject originalImageObject = saveLinkedImageObject(operator, region, "contents/test/active-original.webp");
        Content content = saveContent(operator, region, ContentStatus.PUBLISHED, originalImageObject);
        contentRevisionRepository.saveAndFlush(new ContentRevision(
            content,
            1,
            content.getVersionNo(),
            operator,
            ContentRevisionStatus.EDIT_REQUESTED,
            "Existing title",
            "Existing description",
            "Existing location",
            "Existing hours",
            "055-000-0000",
            "Existing precautions",
            "Existing age",
            "Existing materials",
            "Existing policy",
            null,
            Instant.now(),
            null,
            null,
            null,
            null,
            null,
            null
        ));

        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/revisions", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(revisionRequestWithoutImageAndPublishAt()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertThat(contentRevisionRepository.count()).isEqualTo(1);
    }

    @Test
    void createContentRevision_whenImageMetadataDoesNotMatch_rollsBackChanges()
        throws Exception {

        AppUser operator = saveUser("revision-invalid-image-operator@example.com");
        Region region = saveRegion("REVISION-INVALID-IMAGE");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject originalImageObject = saveLinkedImageObject(operator, region, "contents/test/revision-invalid-original.webp");
        Content content = saveContent(operator, region, ContentStatus.PUBLISHED, originalImageObject);
        ImageObject candidateImageObject = saveImageObject(operator, region, "contents/test/revision-invalid-candidate.webp");
        imageStorageGateway.addMetadata(candidateImageObject.getObjectKey(), 1L, candidateImageObject.getChecksum());

        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/revisions", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(revisionRequestWithoutPublishAt(candidateImageObject.getImageObjectId().toString())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(contentRevisionRepository.count()).isZero();
        assertThat(imageObjectRepository.findById(candidateImageObject.getImageObjectId()))
            .get()
            .satisfies(candidate -> assertThat(candidate.getLinkedAt()).isNull());
    }

    @Test
    void createContentRevision_whenRepresentativeImageObjectIdIsNotJsonString_returnsInvalidType()
        throws Exception {

        AppUser operator = saveUser("revision-image-type-operator@example.com");
        Region region = saveRegion("REVISION-IMAGE-TYPE");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject originalImageObject = saveLinkedImageObject(operator, region, "contents/test/revision-image-type.webp");
        Content content = saveContent(operator, region, ContentStatus.PUBLISHED, originalImageObject);

        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/revisions", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(nonStringRevisionImageObjectIdRequest()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        assertThat(contentRevisionRepository.count()).isZero();
    }

    @Test
    void createContentRevision_whenAuthorizationHeaderIsMissing_returnsUnauthenticatedWithoutCreatingRevision()
        throws Exception {

        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/revisions", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(revisionRequestWithoutImageAndPublishAt()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(contentRevisionRepository.count()).isZero();
    }

    @Test
    void createContentRevision_whenUserIsNotOperator_returnsForbiddenWithoutCreatingRevision()
        throws Exception {

        AppUser visitor = saveUser("revision-visitor@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(visitor, UserRole.VISITOR, null));

        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/revisions", 1)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(visitor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(revisionRequestWithoutImageAndPublishAt()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(contentRevisionRepository.count()).isZero();
    }

    @Test
    void createContentRevision_whenContentBelongsToOtherOperator_returnsForbiddenWithoutCreatingRevision()
        throws Exception {

        AppUser owner = saveUser("revision-owner@example.com");
        AppUser operator = saveUser("revision-other-operator@example.com");
        Region region = saveRegion("REVISION-OTHER-OPERATOR");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(owner, UserRole.OPERATOR, region));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject originalImageObject = saveLinkedImageObject(owner, region, "contents/test/other-operator-original.webp");
        Content content = saveContent(owner, region, ContentStatus.PUBLISHED, originalImageObject);

        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/revisions", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(revisionRequestWithoutImageAndPublishAt()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(contentRevisionRepository.count()).isZero();
    }

    @Test
    void createContentRevision_whenContentDoesNotExist_returnsNotFoundWithoutCreatingRevision()
        throws Exception {

        AppUser operator = saveUser("revision-not-found-operator@example.com");
        Region region = saveRegion("REVISION-NOT-FOUND");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));

        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/revisions", 9_999_999)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(revisionRequestWithoutImageAndPublishAt()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(contentRevisionRepository.count()).isZero();
    }

    @Test
    void createContentRevision_whenImageBelongsToOtherOperator_returnsInvalidInputWithoutLinkingImage()
        throws Exception {

        AppUser operator = saveUser("revision-image-owner-operator@example.com");
        AppUser imageOwner = saveUser("revision-image-other-operator@example.com");
        Region region = saveRegion("REVISION-IMAGE-OTHER-OPERATOR");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(imageOwner, UserRole.OPERATOR, region));
        ImageObject originalImageObject = saveLinkedImageObject(operator, region, "contents/test/image-owner-original.webp");
        Content content = saveContent(operator, region, ContentStatus.PUBLISHED, originalImageObject);
        ImageObject candidateImageObject = saveImageObject(imageOwner, region, "contents/test/image-other-operator.webp");
        imageStorageGateway.addMetadata(
            candidateImageObject.getObjectKey(),
            candidateImageObject.getByteSize(),
            candidateImageObject.getChecksum()
        );

        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/revisions", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(revisionRequestWithoutPublishAt(candidateImageObject.getImageObjectId().toString())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(contentRevisionRepository.count()).isZero();
        assertThat(imageObjectRepository.findById(candidateImageObject.getImageObjectId()))
            .get()
            .satisfies(candidate -> assertThat(candidate.getLinkedAt()).isNull());
    }

    @Test
    void createContentRevision_whenImageBelongsToOtherRegion_returnsInvalidInputWithoutLinkingImage()
        throws Exception {

        AppUser operator = saveUser("revision-image-region-operator@example.com");
        Region contentRegion = saveRegion("REVISION-IMAGE-CONTENT-REGION");
        Region imageRegion = saveRegion("REVISION-IMAGE-OTHER-REGION");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, contentRegion));
        ImageObject originalImageObject = saveLinkedImageObject(
            operator,
            contentRegion,
            "contents/test/image-region-original.webp"
        );
        Content content = saveContent(operator, contentRegion, ContentStatus.PUBLISHED, originalImageObject);
        ImageObject candidateImageObject = saveImageObject(operator, imageRegion, "contents/test/image-other-region.webp");
        imageStorageGateway.addMetadata(
            candidateImageObject.getObjectKey(),
            candidateImageObject.getByteSize(),
            candidateImageObject.getChecksum()
        );

        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/revisions", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(revisionRequestWithoutPublishAt(candidateImageObject.getImageObjectId().toString())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(contentRevisionRepository.count()).isZero();
        assertThat(imageObjectRepository.findById(candidateImageObject.getImageObjectId()))
            .get()
            .satisfies(candidate -> assertThat(candidate.getLinkedAt()).isNull());
    }

    @Test
    void createContentRevision_whenImageUploadIsExpired_returnsInvalidInputWithoutLinkingImage()
        throws Exception {

        AppUser operator = saveUser("revision-image-expired-operator@example.com");
        Region region = saveRegion("REVISION-IMAGE-EXPIRED");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject originalImageObject = saveLinkedImageObject(operator, region, "contents/test/image-expired-original.webp");
        Content content = saveContent(operator, region, ContentStatus.PUBLISHED, originalImageObject);
        ImageObject candidateImageObject = saveExpiredImageObject(operator, region, "contents/test/image-expired.webp");
        imageStorageGateway.addMetadata(
            candidateImageObject.getObjectKey(),
            candidateImageObject.getByteSize(),
            candidateImageObject.getChecksum()
        );

        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/revisions", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(revisionRequestWithoutPublishAt(candidateImageObject.getImageObjectId().toString())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(contentRevisionRepository.count()).isZero();
        assertThat(imageObjectRepository.findById(candidateImageObject.getImageObjectId()))
            .get()
            .satisfies(candidate -> assertThat(candidate.getLinkedAt()).isNull());
    }

    @Test
    void createContentRevision_whenImageIsAlreadyLinked_returnsInvalidInputWithoutCreatingRevision()
        throws Exception {

        AppUser operator = saveUser("revision-image-linked-operator@example.com");
        Region region = saveRegion("REVISION-IMAGE-LINKED");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        ImageObject originalImageObject = saveLinkedImageObject(operator, region, "contents/test/image-linked-original.webp");
        Content content = saveContent(operator, region, ContentStatus.PUBLISHED, originalImageObject);
        ImageObject candidateImageObject = saveLinkedImageObject(operator, region, "contents/test/image-linked-candidate.webp");
        Instant originalLinkedAt = candidateImageObject.getLinkedAt();

        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/revisions", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(revisionRequestWithoutPublishAt(candidateImageObject.getImageObjectId().toString())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(contentRevisionRepository.count()).isZero();
        assertThat(imageObjectRepository.findById(candidateImageObject.getImageObjectId()))
            .get()
            .satisfies(candidate -> assertThat(candidate.getLinkedAt()).isEqualTo(originalLinkedAt));
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

    private ImageObject saveExpiredImageObject(AppUser operator, Region region, String objectKey) {
        return imageObjectRepository.saveAndFlush(ImageObject.createUploadCandidate(
            objectKey,
            operator,
            region,
            "image/webp",
            524_288L,
            CHECKSUM,
            Instant.now().minusSeconds(1)
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
        ContentStatus status,
        ImageObject representativeImageObject
    ) {
        Content content = new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            status,
            "Original title",
            "Original description",
            "Original location",
            "Original hours",
            "055-000-0000",
            "Original precautions",
            "Original age",
            "Original materials",
            "Original policy",
            Instant.parse("2026-08-15T00:00:00Z")
        );
        content.assignRepresentativeImage(representativeImageObject, Instant.now().minusSeconds(30));
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

    private String revisionRequestWithoutImage() {
        return """
            {
              "title": "Updated title",
              "description": "Updated description",
              "locationText": "Updated location",
              "operatingHoursText": "Updated hours",
              "contactText": "055-111-1111",
              "precautions": "Updated precautions",
              "ageRequirement": "Updated age",
              "materials": "Updated materials",
              "cancellationPolicyText": "Updated policy",
              "publishAt": "2026-08-20T09:00:00+09:00"
            }
            """;
    }

    private String revisionRequestWithoutImageAndPublishAt() {
        return """
            {
              "title": "Updated title",
              "description": "Updated description",
              "locationText": "Updated location",
              "operatingHoursText": "Updated hours",
              "contactText": "055-111-1111",
              "precautions": "Updated precautions",
              "ageRequirement": "Updated age",
              "materials": "Updated materials",
              "cancellationPolicyText": "Updated policy"
            }
            """;
    }

    private String revisionRequestWithoutPublishAt(String representativeImageObjectId) {
        return """
            {
              "title": "Updated title",
              "description": "Updated description",
              "locationText": "Updated location",
              "operatingHoursText": "Updated hours",
              "contactText": "055-111-1111",
              "precautions": "Updated precautions",
              "ageRequirement": "Updated age",
              "materials": "Updated materials",
              "cancellationPolicyText": "Updated policy",
              "representativeImageObjectId": "%s"
            }
            """.formatted(representativeImageObjectId);
    }

    private String nonStringRevisionImageObjectIdRequest() {
        return """
            {
              "title": "Updated title",
              "description": "Updated description",
              "locationText": "Updated location",
              "operatingHoursText": "Updated hours",
              "contactText": "055-111-1111",
              "precautions": "Updated precautions",
              "ageRequirement": "Updated age",
              "materials": "Updated materials",
              "cancellationPolicyText": "Updated policy",
              "representativeImageObjectId": 1
            }
            """;
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
