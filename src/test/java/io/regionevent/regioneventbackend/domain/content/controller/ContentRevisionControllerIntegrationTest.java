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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
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
class ContentRevisionControllerIntegrationTest {

    private static final Instant ORIGINAL_PUBLISH_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final Instant CANDIDATE_PUBLISH_AT = Instant.parse("2026-08-06T00:00:00Z");
    private static final String REJECT_REASON = "공개 예정 시각과 운영 시간의 정합성을 보완해 주세요.";

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;

    @Autowired
    ContentRevisionControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentRevisionRepository contentRevisionRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        JwtAccessTokenService jwtAccessTokenService,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.entityManager = entityManager;
    }

    @Test
    void rejectContentRevision_whenPublishedRevisionIsValid_rejectsOnlyRevisionAndRecordsAudit() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null);
        int originalVersion = fixture.content().getVersionNo();

        performReject(
            fixture.admin(),
            fixture.revision().getContentRevisionId().toString(),
            "{\"reason\":\"  " + REJECT_REASON + "  \"}"
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 수정본 반려에 성공했습니다."))
            .andExpect(jsonPath("$.data.revisionId").value(fixture.revision().getContentRevisionId().toString()))
            .andExpect(jsonPath("$.data.contentId").value(fixture.content().getContentId().toString()))
            .andExpect(jsonPath("$.data.revisionStatus").value("EDIT_REJECTED"))
            .andExpect(jsonPath("$.data.contentStatus").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.reviewReason").value(REJECT_REASON))
            .andExpect(jsonPath("$.data.reviewedAt").isString());

        entityManager.flush();
        entityManager.clear();
        ContentRevision rejectedRevision = contentRevisionRepository.findById(
            fixture.revision().getContentRevisionId()
        ).orElseThrow();
        Content unchangedContent = contentRepository.findById(fixture.content().getContentId()).orElseThrow();
        assertThat(rejectedRevision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REJECTED);
        assertThat(rejectedRevision.getReviewedBy().getUserId()).isEqualTo(fixture.admin().getUserId());
        assertThat(rejectedRevision.getReviewReason()).isEqualTo(REJECT_REASON);
        assertThat(unchangedContent.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(unchangedContent.getPublishAt()).isEqualTo(ORIGINAL_PUBLISH_AT);
        assertThat(unchangedContent.getVersionNo()).isEqualTo(originalVersion);
        assertThat(unchangedContent.getTitle()).isEqualTo("원본 제목");

        assertSuccessfulAudit(fixture, rejectedRevision.getReviewedAt());
    }

    @Test
    void rejectContentRevision_whenPrePublicationRevisionIsValid_keepsOriginalPending() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, CANDIDATE_PUBLISH_AT);
        int originalVersion = fixture.content().getVersionNo();

        performReject(
            fixture.admin(),
            fixture.revision().getContentRevisionId().toString(),
            "{\"reason\":\"" + REJECT_REASON + "\"}"
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.revisionStatus").value("EDIT_REJECTED"))
            .andExpect(jsonPath("$.data.contentStatus").value("PENDING"));

        entityManager.flush();
        entityManager.clear();
        Content unchangedContent = contentRepository.findById(fixture.content().getContentId()).orElseThrow();
        ContentRevision rejectedRevision = contentRevisionRepository.findById(
            fixture.revision().getContentRevisionId()
        ).orElseThrow();
        assertThat(unchangedContent.getStatus()).isEqualTo(ContentStatus.PENDING);
        assertThat(unchangedContent.getPublishAt()).isEqualTo(ORIGINAL_PUBLISH_AT);
        assertThat(unchangedContent.getVersionNo()).isEqualTo(originalVersion);
        assertThat(unchangedContent.getTitle()).isEqualTo("원본 제목");
        assertThat(rejectedRevision.getPublishAt()).isEqualTo(CANDIDATE_PUBLISH_AT);
        assertThat(rejectedRevision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REJECTED);
        assertSuccessfulAudit(fixture, rejectedRevision.getReviewedAt());
    }

    @Test
    void rejectContentRevision_whenAdminRegionDiffers_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null);
        Region otherRegion = saveRegion("OTHER");
        AppUser otherRegionAdmin = saveUser("other-admin", AppUserStatus.ACTIVE);
        assignRegionAdmin(otherRegionAdmin, otherRegion);

        performReject(
            otherRegionAdmin,
            fixture.revision().getContentRevisionId().toString(),
            "{\"reason\":\"" + REJECT_REASON + "\"}"
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertUnchanged(fixture);
    }

    @Test
    void rejectContentRevision_whenAdminIsNotActive_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null);
        AppUser withdrawingAdmin = saveUser("withdrawing-admin", AppUserStatus.WITHDRAWING);
        assignRegionAdmin(withdrawingAdmin, fixture.region());

        performReject(
            withdrawingAdmin,
            fixture.revision().getContentRevisionId().toString(),
            "{\"reason\":\"" + REJECT_REASON + "\"}"
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertUnchanged(fixture);
    }

    @Test
    void rejectContentRevision_whenOriginalIsSoftDeleted_returnsNotFound() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, CANDIDATE_PUBLISH_AT);
        fixture.content().softDelete();
        contentRepository.flush();

        performReject(
            fixture.admin(),
            fixture.revision().getContentRevisionId().toString(),
            "{\"reason\":\"" + REJECT_REASON + "\"}"
        )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(fixture.revision().getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REQUESTED);
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void rejectContentRevision_whenRevisionIsAlreadyTerminal_returnsContentStateConflict() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null);
        fixture.revision().reject(
            fixture.admin(),
            Instant.parse("2026-08-02T00:00:00Z"),
            "이미 처리한 사유"
        );
        contentRevisionRepository.flush();

        performReject(
            fixture.admin(),
            fixture.revision().getContentRevisionId().toString(),
            "{\"reason\":\"" + REJECT_REASON + "\"}"
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertThat(fixture.revision().getReviewReason()).isEqualTo("이미 처리한 사유");
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void rejectContentRevision_whenOriginalStateAndCandidatePublishAtConflict_returnsConflict() throws Exception {
        Fixture publishedWithCandidate = createFixture(ContentStatus.PUBLISHED, CANDIDATE_PUBLISH_AT);
        Fixture pendingWithoutCandidate = createFixture(ContentStatus.PENDING, null);

        performReject(
            publishedWithCandidate.admin(),
            publishedWithCandidate.revision().getContentRevisionId().toString(),
            "{\"reason\":\"" + REJECT_REASON + "\"}"
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));
        performReject(
            pendingWithoutCandidate.admin(),
            pendingWithoutCandidate.revision().getContentRevisionId().toString(),
            "{\"reason\":\"" + REJECT_REASON + "\"}"
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertThat(publishedWithCandidate.revision().getStatus())
            .isEqualTo(ContentRevisionStatus.EDIT_REQUESTED);
        assertThat(pendingWithoutCandidate.revision().getStatus())
            .isEqualTo(ContentRevisionStatus.EDIT_REQUESTED);
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void rejectContentRevision_whenRevisionDoesNotExist_returnsNotFound() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null);

        performReject(fixture.admin(), "999999999", "{\"reason\":\"" + REJECT_REASON + "\"}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void rejectContentRevision_whenInputIsInvalid_returnsContractErrors() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null);

        for (String invalidRevisionId : new String[]{"0", "01", "+1", "not-a-number", "9223372036854775808"}) {
            performReject(fixture.admin(), invalidRevisionId, "{\"reason\":\"" + REJECT_REASON + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        performReject(
            fixture.admin(),
            fixture.revision().getContentRevisionId().toString(),
            "{\"reason\":\"   \"}"
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        performReject(
            fixture.admin(),
            fixture.revision().getContentRevisionId().toString(),
            "{"
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));

        assertUnchanged(fixture);
    }

    @Test
    void rejectContentRevision_withoutAuthentication_returnsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/region-admin/content-revisions/1/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + REJECT_REASON + "\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private void assertSuccessfulAudit(Fixture fixture, Instant reviewedAt) {
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getRegion().getRegionId()).isEqualTo(fixture.region().getRegionId());
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT);
            assertThat(auditEvent.getTargetId()).isEqualTo(fixture.content().getContentId());
            assertThat(auditEvent.getPreviousState()).isEqualTo("EDIT_REQUESTED");
            assertThat(auditEvent.getNextState()).isEqualTo("EDIT_REJECTED");
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(auditEvent.getReasonCode()).isNull();
            assertThat(auditEvent.getOccurredAt()).isEqualTo(reviewedAt);
            assertThat(auditEvent.getActorRole()).isEqualTo("REGION_ADMIN");
            assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                .hasValueSatisfying(actorLink ->
                    assertThat(actorLink.getActor().getUserId()).isEqualTo(fixture.admin().getUserId())
                );
        });
    }

    private void assertUnchanged(Fixture fixture) {
        assertThat(fixture.revision().getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REQUESTED);
        assertThat(fixture.revision().getReviewedAt()).isNull();
        assertThat(fixture.revision().getReviewedBy()).isNull();
        assertThat(fixture.revision().getReviewReason()).isNull();
        assertThat(auditEventRepository.count()).isZero();
    }

    private ResultActions performReject(AppUser user, String revisionId, String requestBody) throws Exception {
        return mockMvc.perform(post(
            "/api/v1/region-admin/content-revisions/{revisionId}/reject",
            revisionId
        )
            .header("Authorization", "Bearer " + jwtAccessTokenService.issue(user.getUserId()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody));
    }

    private Fixture createFixture(ContentStatus contentStatus, Instant candidatePublishAt) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = saveRegion("R" + suffix);
        AppUser admin = saveUser("admin-" + suffix, AppUserStatus.ACTIVE);
        assignRegionAdmin(admin, region);
        AppUser operator = saveUser("operator-" + suffix, AppUserStatus.ACTIVE);
        Content content = contentRepository.saveAndFlush(new Content(
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
        ));
        ContentRevision revision = contentRevisionRepository.saveAndFlush(new ContentRevision(
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
            Instant.parse("2026-08-01T00:00:00Z"),
            null,
            null,
            null,
            null,
            null,
            null
        ));
        return new Fixture(region, admin, content, revision);
    }

    private Region saveRegion(String codePrefix) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return regionRepository.saveAndFlush(new Region(codePrefix + suffix, codePrefix + " 지역", true));
    }

    private AppUser saveUser(String identifierPrefix, AppUserStatus status) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            identifierPrefix + suffix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            status
        ));
    }

    private void assignRegionAdmin(AppUser user, Region region) {
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.REGION_ADMIN, region));
    }

    private record Fixture(
        Region region,
        AppUser admin,
        Content content,
        ContentRevision revision
    ) {
    }
}
