package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
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
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContentRevisionApprovalControllerIntegrationTest {

    private static final Instant ORIGINAL_PUBLISH_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final Instant CANDIDATE_PUBLISH_AT = Instant.parse("2026-08-20T00:00:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-01T00:00:00Z");

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ImageObjectRepository imageObjectRepository;
    private final ContentRepository contentRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final ContentLogRepository contentLogRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;

    @Autowired
    ContentRevisionApprovalControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ImageObjectRepository imageObjectRepository,
        ContentRepository contentRepository,
        ContentRevisionRepository contentRevisionRepository,
        ContentLogRepository contentLogRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        JwtAccessTokenService jwtAccessTokenService,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.imageObjectRepository = imageObjectRepository;
        this.contentRepository = contentRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.contentLogRepository = contentLogRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.entityManager = entityManager;
    }

    @Test
    void approveContentRevision_whenPublishedRevisionIsValid_appliesCandidateAndKeepsPublished() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null, false);
        int originalVersion = fixture.content().getVersionNo();

        performApprove(fixture.admin(), fixture.revision().getContentRevisionId().toString())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 수정본 승인에 성공했습니다."))
            .andExpect(jsonPath("$.data.revisionId").value(fixture.revision().getContentRevisionId().toString()))
            .andExpect(jsonPath("$.data.contentId").value(fixture.content().getContentId().toString()))
            .andExpect(jsonPath("$.data.revisionStatus").value("EDIT_APPROVED"))
            .andExpect(jsonPath("$.data.contentStatus").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.publishAt").value(ORIGINAL_PUBLISH_AT.toString()))
            .andExpect(jsonPath("$.data.reviewedAt").isString());

        ApprovedState approvedState = findApprovedState(fixture);
        assertCandidateApplied(approvedState, fixture, ContentStatus.PUBLISHED, ORIGINAL_PUBLISH_AT);
        assertThat(approvedState.content().getVersionNo()).isEqualTo(originalVersion + 1);
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(
            fixture.content().getContentId()
        )).isEmpty();
        assertSuccessfulAudit(fixture, approvedState.revision().getReviewedAt());
    }

    @Test
    void approveContentRevision_whenPrePublicationRevisionIsValid_appliesCandidateAndRestoresApproved()
        throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, CANDIDATE_PUBLISH_AT, true);

        performApprove(fixture.admin(), fixture.revision().getContentRevisionId().toString())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.revisionStatus").value("EDIT_APPROVED"))
            .andExpect(jsonPath("$.data.contentStatus").value("APPROVED"))
            .andExpect(jsonPath("$.data.publishAt").value(CANDIDATE_PUBLISH_AT.toString()));

        ApprovedState approvedState = findApprovedState(fixture);
        assertCandidateApplied(approvedState, fixture, ContentStatus.APPROVED, CANDIDATE_PUBLISH_AT);
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(
            fixture.content().getContentId()
        )).extracting(ContentLog::getStatus)
            .containsExactly(
                ContentLogStatus.APPROVED,
                ContentLogStatus.PENDING,
                ContentLogStatus.APPROVED
            );
        assertSuccessfulAudit(fixture, approvedState.revision().getReviewedAt());
    }

    @Test
    void approveContentRevision_whenAdminRegionDiffers_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null, false);
        Region otherRegion = saveRegion("OTHER");
        AppUser otherAdmin = saveUser("other-admin", AppUserStatus.ACTIVE);
        assignRegionAdmin(otherAdmin, otherRegion);

        performApprove(otherAdmin, fixture.revision().getContentRevisionId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertUnchanged(fixture);
    }

    @Test
    void approveContentRevision_whenAdminIsInactive_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null, false);
        AppUser inactiveAdmin = saveUser("inactive-admin", AppUserStatus.WITHDRAWING);
        assignRegionAdmin(inactiveAdmin, fixture.region());

        performApprove(inactiveAdmin, fixture.revision().getContentRevisionId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertUnchanged(fixture);
    }

    @Test
    void approveContentRevision_whenOriginalIsSoftDeleted_returnsNotFound() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, CANDIDATE_PUBLISH_AT, true);
        fixture.content().softDelete();
        contentRepository.flush();

        performApprove(fixture.admin(), fixture.revision().getContentRevisionId().toString())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(fixture.revision().getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REQUESTED);
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void approveContentRevision_whenRevisionIsTerminal_returnsContentStateConflict() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null, false);
        fixture.revision().reject(fixture.admin(), SUBMITTED_AT.plusSeconds(60), "반려 사유");
        contentRevisionRepository.flush();

        performApprove(fixture.admin(), fixture.revision().getContentRevisionId().toString())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertThat(fixture.revision().getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REJECTED);
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void approveContentRevision_whenBaseVersionDiffers_returnsContentStateConflict() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null, false);
        fixture.content().replaceEditableFields(
            "변경된 원본 제목",
            fixture.content().getDescription(),
            fixture.content().getLocationText(),
            fixture.content().getOperatingHoursText(),
            fixture.content().getContactText(),
            fixture.content().getPrecautions(),
            fixture.content().getAgeRequirement(),
            fixture.content().getMaterials(),
            fixture.content().getCancellationPolicyText(),
            fixture.content().getPublishAt()
        );
        contentRepository.flush();

        performApprove(fixture.admin(), fixture.revision().getContentRevisionId().toString())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertThat(fixture.revision().getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REQUESTED);
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void approveContentRevision_whenStatePublishAtOrHistoryConflicts_returnsContentStateConflict()
        throws Exception {
        Fixture publishedWithCandidate = createFixture(
            ContentStatus.PUBLISHED,
            CANDIDATE_PUBLISH_AT,
            false
        );
        Fixture pendingWithoutCandidate = createFixture(ContentStatus.PENDING, null, true);
        Fixture pendingWithoutApprovedHistory = createFixture(
            ContentStatus.PENDING,
            CANDIDATE_PUBLISH_AT,
            false
        );

        for (Fixture fixture : new Fixture[]{
            publishedWithCandidate,
            pendingWithoutCandidate,
            pendingWithoutApprovedHistory
        }) {
            performApprove(fixture.admin(), fixture.revision().getContentRevisionId().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));
            assertUnchanged(fixture);
        }
    }

    @Test
    void approveContentRevision_whenRevisionDoesNotExist_returnsNotFound() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null, false);

        performApprove(fixture.admin(), "999999999")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void approveContentRevision_whenRevisionIdIsInvalid_returnsInvalidInput() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null, false);

        for (String invalidRevisionId : new String[]{"0", "01", "+1", "not-a-number", "9223372036854775808"}) {
            performApprove(fixture.admin(), invalidRevisionId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        assertUnchanged(fixture);
    }

    @Test
    void approveContentRevision_withoutAuthentication_returnsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/region-admin/content-revisions/1/approve"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private ResultActions performApprove(AppUser user, String revisionId) throws Exception {
        return mockMvc.perform(post(
            "/api/v1/region-admin/content-revisions/{revisionId}/approve",
            revisionId
        ).header("Authorization", "Bearer " + jwtAccessTokenService.issue(user.getUserId())));
    }

    private Fixture createFixture(
        ContentStatus contentStatus,
        Instant candidatePublishAt,
        boolean prePublicationHistory
    ) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = saveRegion("R" + suffix);
        AppUser admin = saveUser("admin-" + suffix, AppUserStatus.ACTIVE);
        assignRegionAdmin(admin, region);
        AppUser operator = saveUser("operator-" + suffix, AppUserStatus.ACTIVE);
        ImageObject originalImage = saveLinkedImage("original-" + suffix, operator, region);
        Content content = new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            contentStatus,
            "원본 제목",
            "원본 설명",
            "원본 장소",
            "원본 운영 시간",
            "055-1234-5678",
            "원본 주의사항",
            "만 7세 이상",
            "편한 복장",
            "원본 취소 정책",
            ORIGINAL_PUBLISH_AT
        );
        content.assignRepresentativeImage(originalImage, SUBMITTED_AT.minusSeconds(600));
        contentRepository.saveAndFlush(content);
        if (prePublicationHistory) {
            contentLogRepository.saveAndFlush(new ContentLog(
                content,
                admin,
                ContentLogStatus.APPROVED,
                null,
                SUBMITTED_AT.minusSeconds(120)
            ));
            contentLogRepository.saveAndFlush(new ContentLog(
                content,
                operator,
                ContentLogStatus.PENDING,
                null,
                SUBMITTED_AT.minusSeconds(60)
            ));
        }
        ImageObject candidateImage = saveLinkedImage("candidate-" + suffix, operator, region);
        ContentRevision revision = new ContentRevision(
            content,
            1,
            content.getVersionNo(),
            operator,
            ContentRevisionStatus.EDIT_REQUESTED,
            "후보 제목",
            "후보 설명",
            "후보 장소",
            "후보 운영 시간",
            "055-9876-5432",
            "후보 주의사항",
            "만 8세 이상",
            "운동화",
            "후보 취소 정책",
            candidatePublishAt,
            SUBMITTED_AT,
            null,
            null,
            null,
            null,
            null,
            null
        );
        revision.assignCandidateImage(candidateImage, SUBMITTED_AT);
        contentRevisionRepository.saveAndFlush(revision);
        return new Fixture(region, admin, content, revision, candidateImage, contentStatus);
    }

    private ApprovedState findApprovedState(Fixture fixture) {
        entityManager.flush();
        entityManager.clear();
        return new ApprovedState(
            contentRepository.findById(fixture.content().getContentId()).orElseThrow(),
            contentRevisionRepository.findById(fixture.revision().getContentRevisionId()).orElseThrow()
        );
    }

    private void assertCandidateApplied(
        ApprovedState approvedState,
        Fixture fixture,
        ContentStatus expectedStatus,
        Instant expectedPublishAt
    ) {
        assertThat(approvedState.content().getStatus()).isEqualTo(expectedStatus);
        assertThat(approvedState.content().getPublishAt()).isEqualTo(expectedPublishAt);
        assertThat(approvedState.content().getTitle()).isEqualTo("후보 제목");
        assertThat(approvedState.content().getDescription()).isEqualTo("후보 설명");
        assertThat(approvedState.content().getLocationText()).isEqualTo("후보 장소");
        assertThat(approvedState.content().getOperatingHoursText()).isEqualTo("후보 운영 시간");
        assertThat(approvedState.content().getContactText()).isEqualTo("055-9876-5432");
        assertThat(approvedState.content().getPrecautions()).isEqualTo("후보 주의사항");
        assertThat(approvedState.content().getAgeRequirement()).isEqualTo("만 8세 이상");
        assertThat(approvedState.content().getMaterials()).isEqualTo("운동화");
        assertThat(approvedState.content().getCancellationPolicyText()).isEqualTo("후보 취소 정책");
        assertThat(approvedState.content().getRepresentativeImageObject().getImageObjectId())
            .isEqualTo(fixture.candidateImage().getImageObjectId());
        assertThat(approvedState.revision().getStatus()).isEqualTo(ContentRevisionStatus.EDIT_APPROVED);
        assertThat(approvedState.revision().getReviewedBy().getUserId())
            .isEqualTo(fixture.admin().getUserId());
        assertThat(approvedState.revision().getReviewedAt()).isNotNull();
        assertThat(approvedState.revision().getReviewReason()).isNull();
    }

    private void assertSuccessfulAudit(Fixture fixture, Instant reviewedAt) {
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getRegion().getRegionId()).isEqualTo(fixture.region().getRegionId());
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT);
            assertThat(auditEvent.getTargetId()).isEqualTo(fixture.content().getContentId());
            assertThat(auditEvent.getPreviousState()).isEqualTo("EDIT_REQUESTED");
            assertThat(auditEvent.getNextState()).isEqualTo("EDIT_APPROVED");
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(auditEvent.getOccurredAt()).isEqualTo(reviewedAt);
            assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                .hasValueSatisfying(actorLink ->
                    assertThat(actorLink.getActor().getUserId()).isEqualTo(fixture.admin().getUserId())
                );
        });
    }

    private void assertUnchanged(Fixture fixture) {
        assertThat(fixture.content().getStatus()).isEqualTo(fixture.initialContentStatus());
        assertThat(fixture.content().getTitle()).isEqualTo("원본 제목");
        assertThat(fixture.content().getPublishAt()).isEqualTo(ORIGINAL_PUBLISH_AT);
        assertThat(fixture.revision().getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REQUESTED);
        assertThat(fixture.revision().getReviewedAt()).isNull();
        assertThat(fixture.revision().getReviewReason()).isNull();
        assertThat(auditEventRepository.count()).isZero();
    }

    private Region saveRegion(String prefix) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return regionRepository.saveAndFlush(new Region(prefix + suffix, prefix + " 지역", true));
    }

    private AppUser saveUser(String prefix, AppUserStatus status) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            prefix + suffix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            status
        ));
    }

    private void assignRegionAdmin(AppUser user, Region region) {
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            user,
            UserRole.REGION_ADMIN,
            region
        ));
    }

    private ImageObject saveLinkedImage(String suffix, AppUser operator, Region region) {
        ImageObject imageObject = ImageObject.createUploadCandidate(
            "content/approval-" + suffix + ".webp",
            operator,
            region,
            "image/webp",
            1L,
            "checksum-" + suffix,
            SUBMITTED_AT.plusSeconds(3_600)
        );
        imageObject.markLinked(SUBMITTED_AT.minusSeconds(1));
        return imageObjectRepository.saveAndFlush(imageObject);
    }

    private record Fixture(
        Region region,
        AppUser admin,
        Content content,
        ContentRevision revision,
        ImageObject candidateImage,
        ContentStatus initialContentStatus
    ) {
    }

    private record ApprovedState(
        Content content,
        ContentRevision revision
    ) {
    }
}
