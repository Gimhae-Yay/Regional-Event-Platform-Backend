package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageException;
import io.regionevent.regioneventbackend.domain.image.service.ImageStorageGateway;
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
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ContentDeletionControllerIntegrationTest {

    private static final Instant PUBLISH_AT = Instant.parse("2026-08-05T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private ContentRevisionRepository contentRevisionRepository;

    @Autowired
    private ContentLogRepository contentLogRepository;

    @Autowired
    private ImageObjectRepository imageObjectRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private AuditEventActorLinkRepository auditEventActorLinkRepository;

    @Autowired
    private RecordingImageStorageGateway imageStorageGateway;

    @BeforeEach
    void setUp() {
        imageStorageGateway.reset();
    }

    @AfterEach
    void tearDown() {
        auditEventActorLinkRepository.deleteAllInBatch();
        auditEventRepository.deleteAllInBatch();
        contentLogRepository.deleteAllInBatch();
        contentRevisionRepository.deleteAllInBatch();
        contentRepository.deleteAllInBatch();
        imageObjectRepository.deleteAllInBatch();
        userRoleAssignmentRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
        regionRepository.deleteAllInBatch();
        imageStorageGateway.reset();
    }

    @Test
    void deleteContent_whenPending_deletesAtomicallyAndMovesFailedStorageDeletionToRetry() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, "pending-delete");
        imageStorageGateway.failDeleteFor(fixture.imageObject().getObjectKey());

        performDelete(fixture.admin(), fixture.content().getContentId().toString(), validRequest())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("공개 전 콘텐츠 삭제에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(fixture.content().getContentId().toString()))
            .andExpect(jsonPath("$.data.deletionEventStatus").value("DELETED"))
            .andExpect(jsonPath("$.data.deletedAt").isString())
            .andExpect(jsonPath("$.data.deletionReason").value("행사 준비가 취소되었습니다."));

        assertSuccessfulDeletion(fixture, ContentStatus.PENDING);
        assertThat(imageObjectRepository.findById(fixture.imageObject().getImageObjectId()))
            .get()
            .satisfies(imageObject -> {
                assertThat(imageObject.getLifecycleStatus()).isEqualTo(ImageLifecycleStatus.DELETE_PENDING);
                assertThat(imageObject.getDeleteAttemptCount()).isOne();
                assertThat(imageObject.getLastDeleteAttemptedAt()).isNotNull();
            });
        assertThat(imageStorageGateway.deletedObjectKeys()).containsExactly(fixture.imageObject().getObjectKey());
    }

    @Test
    void deleteContent_whenApproved_deletesStorageObjectOnlyAfterCommit() throws Exception {
        Fixture fixture = createFixture(ContentStatus.APPROVED, "approved-delete");

        performDelete(fixture.admin(), fixture.content().getContentId().toString(), validRequest())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deletionEventStatus").value("DELETED"));

        assertSuccessfulDeletion(fixture, ContentStatus.APPROVED);
        assertThat(imageObjectRepository.existsById(fixture.imageObject().getImageObjectId())).isFalse();
        assertThat(imageStorageGateway.deletedObjectKeys()).containsExactly(fixture.imageObject().getObjectKey());
    }

    @Test
    void deleteContent_whenRevisionStillReferencesImage_keepsSharedImageActive() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, "shared-image");
        saveRevisionReferencing(fixture);

        performDelete(fixture.admin(), fixture.content().getContentId().toString(), validRequest())
            .andExpect(status().isOk());

        assertThat(imageObjectRepository.findById(fixture.imageObject().getImageObjectId()))
            .get()
            .extracting(ImageObject::getLifecycleStatus)
            .isEqualTo(ImageLifecycleStatus.ACTIVE);
        assertThat(imageStorageGateway.deletedObjectKeys()).isEmpty();
    }

    @Test
    void deleteContent_whenInputOrAuthenticationIsInvalid_returnsContractErrorsWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, "invalid-request");

        for (String invalidContentId : new String[]{"0", "-1", "01", "+1"}) {
            performDelete(fixture.admin(), invalidContentId, validRequest())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        for (String invalidTypeContentId : new String[]{"not-a-number", "9223372036854775808"}) {
            performDelete(fixture.admin(), invalidTypeContentId, validRequest())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        }
        performDelete(fixture.admin(), fixture.content().getContentId().toString(), "{\"reason\":\" \"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        performDelete(fixture.admin(), fixture.content().getContentId().toString(), "{\"reason\":")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));
        mockMvc.perform(delete("/api/v1/region-admin/contents/{contentId}", fixture.content().getContentId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertContentUnchanged(fixture);
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void deleteContent_whenAuthorizationTargetOrStateIsInvalid_rollsBackAndRecordsNonPersonalFailure()
        throws Exception {

        Fixture fixture = createFixture(ContentStatus.PUBLISHED, "failure-audit");
        Region otherRegion = saveRegion("OTHER");
        AppUser otherAdmin = saveUser("other-admin");
        assignRole(otherAdmin, UserRole.REGION_ADMIN, otherRegion);
        AppUser visitor = saveUser("visitor");

        performDelete(otherAdmin, fixture.content().getContentId().toString(), validRequest())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        performDelete(visitor, fixture.content().getContentId().toString(), validRequest())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        performDelete(fixture.admin(), fixture.content().getContentId().toString(), validRequest())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_DELETE_CONFLICT"));
        performDelete(fixture.admin(), "999999999", validRequest())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertContentUnchanged(fixture);
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(
            fixture.content().getContentId()
        )).hasSize(1);
        assertThat(auditEventRepository.findAll())
            .hasSize(4)
            .allSatisfy(auditEvent -> {
                assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.FAILURE);
                assertThat(auditEvent.getActorRole()).isNull();
                assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId())).isEmpty();
            });
    }

    @Test
    void deleteContent_whenAlreadyDeleted_returnsConflictWithoutAdditionalDomainChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, "already-deleted");
        fixture.content().softDelete(Instant.now());
        contentRepository.saveAndFlush(fixture.content());

        performDelete(fixture.admin(), fixture.content().getContentId().toString(), validRequest())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_DELETE_CONFLICT"));

        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(
            fixture.content().getContentId()
        )).extracting(ContentLog::getStatus).containsExactly(ContentLogStatus.PENDING);
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.FAILURE);
            assertThat(auditEvent.getReasonCode()).isEqualTo("CONTENT_DELETE_CONFLICT");
            assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId())).isEmpty();
        });
        assertThat(imageStorageGateway.deletedObjectKeys()).isEmpty();
    }

    private void assertSuccessfulDeletion(Fixture fixture, ContentStatus previousStatus) {
        Content deletedContent = contentRepository.findById(fixture.content().getContentId()).orElseThrow();
        assertThat(deletedContent.getStatus()).isEqualTo(previousStatus);
        assertThat(deletedContent.getDeletedAt()).isNotNull();
        assertThat(deletedContent.getRepresentativeImageObject()).isNull();
        assertThat(deletedContent.getRepresentativeImageAssignedAt()).isNull();

        List<ContentLog> logs = contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(
            deletedContent.getContentId()
        );
        assertThat(logs).extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.valueOf(previousStatus.name()), ContentLogStatus.DELETED);
        ContentLog deletedLog = logs.get(1);
        assertThat(deletedLog.getActor().getUserId()).isEqualTo(fixture.admin().getUserId());
        assertThat(deletedLog.getReason()).isEqualTo("행사 준비가 취소되었습니다.");
        assertThat(deletedLog.getDate()).isEqualTo(deletedContent.getDeletedAt());

        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(auditEvent.getPreviousState()).isEqualTo(previousStatus.name());
            assertThat(auditEvent.getNextState()).isEqualTo("DELETED");
            assertThat(auditEvent.getOccurredAt()).isEqualTo(deletedContent.getDeletedAt());
            assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                .hasValueSatisfying(actorLink ->
                    assertThat(actorLink.getActor().getUserId()).isEqualTo(fixture.admin().getUserId())
                );
        });
    }

    private void assertContentUnchanged(Fixture fixture) {
        assertThat(contentRepository.findById(fixture.content().getContentId()))
            .get()
            .satisfies(content -> {
                assertThat(content.getDeletedAt()).isNull();
                assertThat(content.getRepresentativeImageObject().getImageObjectId())
                    .isEqualTo(fixture.imageObject().getImageObjectId());
            });
        assertThat(imageObjectRepository.findById(fixture.imageObject().getImageObjectId()))
            .get()
            .extracting(ImageObject::getLifecycleStatus)
            .isEqualTo(ImageLifecycleStatus.ACTIVE);
        assertThat(imageStorageGateway.deletedObjectKeys()).isEmpty();
    }

    private ResultActions performDelete(AppUser user, String contentId, String body) throws Exception {
        return mockMvc.perform(delete("/api/v1/region-admin/contents/{contentId}", contentId)
            .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private Fixture createFixture(ContentStatus status, String prefix) {
        String suffix = prefix + "-" + Long.toUnsignedString(System.nanoTime());
        Region region = saveRegion(suffix);
        AppUser admin = saveUser("admin-" + suffix);
        assignRole(admin, UserRole.REGION_ADMIN, region);
        AppUser operator = saveUser("operator-" + suffix);
        ImageObject imageObject = saveLinkedImage(operator, region, "content/" + suffix + ".webp");
        Content content = new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            status,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            PUBLISH_AT
        );
        content.assignRepresentativeImage(imageObject, Instant.now().minusSeconds(60));
        contentRepository.saveAndFlush(content);
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            operator,
            ContentLogStatus.valueOf(status.name()),
            status == ContentStatus.REJECTED ? "반려 사유" : null,
            Instant.now().minusSeconds(30)
        ));
        return new Fixture(region, admin, operator, content, imageObject);
    }

    private void saveRevisionReferencing(Fixture fixture) {
        ContentRevision revision = new ContentRevision(
            fixture.content(),
            1,
            fixture.content().getVersionNo(),
            fixture.operator(),
            ContentRevisionStatus.EDIT_REQUESTED,
            "수정 후보 제목",
            "수정 후보 설명",
            "수정 후보 장소",
            "수정 후보 운영 시간",
            "055-9876-5432",
            "수정 후보 주의사항",
            "만 8세 이상",
            "운동화",
            "수정 후보 취소 정책",
            PUBLISH_AT.plusSeconds(3600),
            Instant.now(),
            null,
            null,
            null,
            null,
            null,
            null
        );
        revision.assignCandidateImage(fixture.imageObject(), Instant.now());
        contentRevisionRepository.saveAndFlush(revision);
    }

    private ImageObject saveLinkedImage(AppUser operator, Region region, String objectKey) {
        ImageObject imageObject = ImageObject.createUploadCandidate(
            objectKey,
            operator,
            region,
            "image/webp",
            1024L,
            "checksum",
            Instant.now().plusSeconds(3600)
        );
        imageObject.markLinked(Instant.now());
        return imageObjectRepository.saveAndFlush(imageObject);
    }

    private Region saveRegion(String suffix) {
        return regionRepository.saveAndFlush(new Region("R-" + suffix, "김해시", true));
    }

    private AppUser saveUser(String prefix) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            prefix + "-" + suffix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private void assignRole(AppUser user, UserRole role, Region region) {
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, role, region));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private String validRequest() {
        return "{\"reason\":\"행사 준비가 취소되었습니다.\"}";
    }

    private record Fixture(
        Region region,
        AppUser admin,
        AppUser operator,
        Content content,
        ImageObject imageObject
    ) {
    }

    @TestConfiguration
    static class TestImageStorageConfiguration {

        @Bean
        @Primary
        RecordingImageStorageGateway recordingImageStorageGateway() {
            return new RecordingImageStorageGateway();
        }
    }

    static class RecordingImageStorageGateway implements ImageStorageGateway {

        private final List<String> deletedObjectKeys = new ArrayList<>();
        private final Set<String> failedObjectKeys = new HashSet<>();

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
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public PresignedViewUrl createPresignedGetUrl(String objectKey) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void delete(String objectKey) {
            deletedObjectKeys.add(objectKey);
            if (failedObjectKeys.contains(objectKey)) {
                throw new ImageStorageException("test storage deletion failure");
            }
        }

        void failDeleteFor(String objectKey) {
            failedObjectKeys.add(objectKey);
        }

        List<String> deletedObjectKeys() {
            return List.copyOf(deletedObjectKeys);
        }

        void reset() {
            deletedObjectKeys.clear();
            failedObjectKeys.clear();
        }
    }
}
