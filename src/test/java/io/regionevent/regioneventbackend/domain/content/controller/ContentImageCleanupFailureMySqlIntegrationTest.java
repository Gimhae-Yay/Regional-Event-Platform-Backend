package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway.StoredObjectMetadata;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ContentImageCleanupFailureMySqlIntegrationTest extends NonTransactionalMySqlTestSupport {

    private static final long IMAGE_BYTE_SIZE = 1_024L;
    private static final Instant PUBLISH_AT = Instant.parse("2026-08-20T01:00:00Z");

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final ContentLogRepository contentLogRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private ImageObjectRepository imageObjectRepository;

    @MockitoBean
    private ImageStorageGateway imageStorageGateway;

    @Autowired
    ContentImageCleanupFailureMySqlIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentRevisionRepository contentRevisionRepository,
        ContentLogRepository contentLogRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        JwtAccessTokenService jwtAccessTokenService,
        PlatformTransactionManager transactionManager
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.contentLogRepository = contentLogRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void updateMyContent_whenDatabaseCleanupFails_returnsSuccessAndKeepsCommittedState() throws Exception {
        ContentUpdateFixture fixture = createContentUpdateFixture();
        stubStoredMetadata(fixture.replacementImage());
        failDatabaseCleanup(fixture.previousImage().imageObjectId());

        mockMvc.perform(put("/api/v1/operator/contents/{contentId}", fixture.contentId())
                .header("Authorization", bearerToken(fixture.operatorId()))
                .contentType(APPLICATION_JSON)
                .content(updateRequest(fixture.replacementImage().imageObjectId(), true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.contentId").value(fixture.contentId().toString()))
            .andExpect(jsonPath("$.data.status").value("REJECTED"));

        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> {
                assertThat(content.getTitle()).isEqualTo("수정된 콘텐츠 제목");
                assertThat(content.getRepresentativeImageObject().getImageObjectId())
                    .isEqualTo(fixture.replacementImage().imageObjectId());
            });
        assertDeletePending(fixture.previousImage().imageObjectId());
        verify(imageStorageGateway).delete(fixture.previousImage().objectKey());
    }

    @Test
    void updateContentRevision_whenDatabaseCleanupFails_returnsSuccessAndKeepsCommittedState() throws Exception {
        RevisionUpdateFixture fixture = createRevisionUpdateFixture();
        stubStoredMetadata(fixture.replacementImage());
        failDatabaseCleanup(fixture.previousImage().imageObjectId());

        mockMvc.perform(put("/api/v1/operator/content-revisions/{revisionId}", fixture.revisionId())
                .header("Authorization", bearerToken(fixture.operatorId()))
                .contentType(APPLICATION_JSON)
                .content(updateRequest(fixture.replacementImage().imageObjectId(), false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.revisionId").value(fixture.revisionId().toString()))
            .andExpect(jsonPath("$.data.contentId").value(fixture.contentId().toString()))
            .andExpect(jsonPath("$.data.status").value("EDIT_REJECTED"));

        assertThat(contentRevisionRepository.findById(fixture.revisionId()))
            .hasValueSatisfying(revision -> {
                assertThat(revision.getTitle()).isEqualTo("수정된 콘텐츠 제목");
                assertThat(revision.getCandidateImageObject().getImageObjectId())
                    .isEqualTo(fixture.replacementImage().imageObjectId());
            });
        assertDeletePending(fixture.previousImage().imageObjectId());
        verify(imageStorageGateway).delete(fixture.previousImage().objectKey());
    }

    @Test
    void deleteContent_whenDatabaseCleanupFails_returnsSuccessAndKeepsCommittedState() throws Exception {
        ContentDeletionFixture fixture = createContentDeletionFixture();
        failDatabaseCleanup(fixture.previousImage().imageObjectId());

        mockMvc.perform(delete("/api/v1/region-admin/contents/{contentId}", fixture.contentId())
                .header("Authorization", bearerToken(fixture.adminId()))
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"행사 준비가 취소되었습니다.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.contentId").value(fixture.contentId().toString()))
            .andExpect(jsonPath("$.data.deletionEventStatus").value("DELETED"));

        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> {
                assertThat(content.getDeletedAt()).isNotNull();
                assertThat(content.getRepresentativeImageObject()).isNull();
            });
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.APPROVED, ContentLogStatus.DELETED);
        assertThat(auditEventRepository.findAll())
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
                assertThat(auditEvent.getTargetId()).isEqualTo(fixture.contentId());
                assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId())).isPresent();
            });
        assertDeletePending(fixture.previousImage().imageObjectId());
        verify(imageStorageGateway).delete(fixture.previousImage().objectKey());
    }

    private ContentUpdateFixture createContentUpdateFixture() {
        return transactionTemplate.execute(status -> {
            FixtureUsers users = saveFixtureUsers("CONTENT-UPDATE");
            ImageReference previousImage = saveImage(users, "previous", true);
            ImageReference replacementImage = saveImage(users, "replacement", false);
            Content content = saveContent(users, ContentStatus.REJECTED, previousImage.imageObjectId());
            return new ContentUpdateFixture(
                users.operator().getUserId(),
                content.getContentId(),
                previousImage,
                replacementImage
            );
        });
    }

    private RevisionUpdateFixture createRevisionUpdateFixture() {
        return transactionTemplate.execute(status -> {
            FixtureUsers users = saveFixtureUsers("REVISION-UPDATE");
            ImageReference representativeImage = saveImage(users, "representative", true);
            Content content = saveContent(users, ContentStatus.PUBLISHED, representativeImage.imageObjectId());
            ImageReference previousImage = saveImage(users, "candidate-previous", true);
            ImageReference replacementImage = saveImage(users, "candidate-replacement", false);
            Instant now = Instant.now();
            ContentRevision revision = new ContentRevision(
                content,
                1,
                content.getVersionNo(),
                users.operator(),
                ContentRevisionStatus.EDIT_REJECTED,
                "기존 수정본 제목",
                "기존 수정본 설명",
                "기존 장소",
                "기존 운영 시간",
                "055-1234-5678",
                "기존 주의사항",
                "만 7세 이상",
                "편한 복장",
                "기존 취소 정책",
                null,
                now.minusSeconds(120),
                now.minusSeconds(60),
                users.admin(),
                "수정이 필요합니다.",
                null,
                null,
                null
            );
            revision.assignCandidateImage(
                imageObjectRepository.findById(previousImage.imageObjectId()).orElseThrow(),
                now.minusSeconds(120)
            );
            contentRevisionRepository.saveAndFlush(revision);
            return new RevisionUpdateFixture(
                users.operator().getUserId(),
                content.getContentId(),
                revision.getContentRevisionId(),
                previousImage,
                replacementImage
            );
        });
    }

    private ContentDeletionFixture createContentDeletionFixture() {
        return transactionTemplate.execute(status -> {
            FixtureUsers users = saveFixtureUsers("CONTENT-DELETE");
            ImageReference previousImage = saveImage(users, "previous", true);
            Content content = saveContent(users, ContentStatus.APPROVED, previousImage.imageObjectId());
            contentLogRepository.saveAndFlush(new ContentLog(
                content,
                users.admin(),
                ContentLogStatus.APPROVED,
                null,
                Instant.now().minusSeconds(60)
            ));
            return new ContentDeletionFixture(
                users.admin().getUserId(),
                content.getContentId(),
                previousImage
            );
        });
    }

    private FixtureUsers saveFixtureUsers(String prefix) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region(prefix + "-" + suffix, "김해시", true));
        AppUser operator = saveUser("operator-" + suffix);
        AppUser admin = saveUser("admin-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            operator,
            UserRole.OPERATOR,
            region
        ));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            admin,
            UserRole.REGION_ADMIN,
            region
        ));
        return new FixtureUsers(region, operator, admin, suffix);
    }

    private AppUser saveUser(String prefix) {
        return appUserRepository.saveAndFlush(new AppUser(
            prefix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private Content saveContent(
        FixtureUsers users,
        ContentStatus status,
        Long representativeImageObjectId
    ) {
        Content content = new Content(
            users.region(),
            users.operator(),
            ContentType.EVENT_EXPERIENCE,
            status,
            "기존 콘텐츠 제목",
            "기존 콘텐츠 설명",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            PUBLISH_AT
        );
        ImageObject representativeImage = imageObjectRepository.findById(representativeImageObjectId).orElseThrow();
        content.assignRepresentativeImage(representativeImage, Instant.now().minusSeconds(120));
        return contentRepository.saveAndFlush(content);
    }

    private ImageReference saveImage(
        FixtureUsers users,
        String name,
        boolean linked
    ) {
        String objectKey = "content/cleanup-failure-" + users.suffix() + "-" + name + ".webp";
        String checksum = "checksum-" + users.suffix() + "-" + name;
        ImageObject imageObject = ImageObject.createUploadCandidate(
            objectKey,
            users.operator(),
            users.region(),
            "image/webp",
            IMAGE_BYTE_SIZE,
            checksum,
            Instant.now().plusSeconds(3_600)
        );
        if (linked) {
            imageObject.markLinked(Instant.now().minusSeconds(60));
        }
        imageObjectRepository.saveAndFlush(imageObject);
        return new ImageReference(imageObject.getImageObjectId(), objectKey, checksum);
    }

    private void stubStoredMetadata(ImageReference image) {
        when(imageStorageGateway.findMetadata(image.objectKey()))
            .thenReturn(new StoredObjectMetadata(IMAGE_BYTE_SIZE, image.checksum()));
    }

    private void failDatabaseCleanup(Long imageObjectId) {
        doThrow(new IllegalStateException("database cleanup failed"))
            .when(imageObjectRepository)
            .deleteDeletePendingObjectWithoutDirectReferences(
                imageObjectId,
                ImageLifecycleStatus.DELETE_PENDING
            );
    }

    private void assertDeletePending(Long imageObjectId) {
        assertThat(imageObjectRepository.findById(imageObjectId))
            .get()
            .extracting(ImageObject::getLifecycleStatus)
            .isEqualTo(ImageLifecycleStatus.DELETE_PENDING);
    }

    private String bearerToken(Long userId) {
        return "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, userId);
    }

    private String updateRequest(Long replacementImageObjectId, boolean includePublishAt) {
        String publishAt = includePublishAt
            ? "\"publishAt\":\"2026-08-20T10:00:00+09:00\","
            : "";
        return """
            {
              "title":"수정된 콘텐츠 제목",
              "description":"수정된 콘텐츠 설명",
              "locationText":"김해문화의전당",
              "operatingHoursText":"매일 10:00~18:00",
              "contactText":"055-1234-5678",
              "precautions":"안내를 따라주세요.",
              "ageRequirement":"만 7세 이상",
              "materials":"편한 복장",
              "cancellationPolicyText":"시작 하루 전까지 취소할 수 있습니다.",
              "reservationPrice":0,
              %s
              "representativeImageObjectId":"%d"
            }
            """.formatted(publishAt, replacementImageObjectId);
    }

    private record FixtureUsers(
        Region region,
        AppUser operator,
        AppUser admin,
        String suffix
    ) {
    }

    private record ImageReference(
        Long imageObjectId,
        String objectKey,
        String checksum
    ) {
    }

    private record ContentUpdateFixture(
        Long operatorId,
        Long contentId,
        ImageReference previousImage,
        ImageReference replacementImage
    ) {
    }

    private record RevisionUpdateFixture(
        Long operatorId,
        Long contentId,
        Long revisionId,
        ImageReference previousImage,
        ImageReference replacementImage
    ) {
    }

    private record ContentDeletionFixture(
        Long adminId,
        Long contentId,
        ImageReference previousImage
    ) {
    }
}
