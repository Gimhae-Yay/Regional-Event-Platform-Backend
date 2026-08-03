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
class WithdrawContentRevisionControllerIntegrationTest {

    private static final Instant ORIGINAL_PUBLISH_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final Instant CANDIDATE_PUBLISH_AT = Instant.parse("2026-08-06T00:00:00Z");
    private static final String WITHDRAWAL_REASON = "operator withdrawal reason";

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
    WithdrawContentRevisionControllerIntegrationTest(
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
    void withdrawContentRevision_whenPublishedRevisionIsValid_withdrawsOnlyRevisionAndRecordsAudit()
        throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null);
        int originalVersion = fixture.content().getVersionNo();

        performWithdraw(
            fixture.operator(),
            fixture.revision().getContentRevisionId().toString(),
            "{\"reason\":\"  " + WITHDRAWAL_REASON + "  \"}"
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 수정본 철회에 성공했습니다."))
            .andExpect(jsonPath("$.data.revisionId").value(fixture.revision().getContentRevisionId().toString()))
            .andExpect(jsonPath("$.data.contentId").value(fixture.content().getContentId().toString()))
            .andExpect(jsonPath("$.data.status").value("EDIT_WITHDRAWN"))
            .andExpect(jsonPath("$.data.withdrawalReason").value(WITHDRAWAL_REASON))
            .andExpect(jsonPath("$.data.withdrawnAt").isString());

        entityManager.flush();
        entityManager.clear();
        ContentRevision withdrawnRevision = contentRevisionRepository.findById(
            fixture.revision().getContentRevisionId()
        ).orElseThrow();
        Content unchangedContent = contentRepository.findById(fixture.content().getContentId()).orElseThrow();
        assertThat(withdrawnRevision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_WITHDRAWN);
        assertThat(withdrawnRevision.getWithdrawnBy().getUserId()).isEqualTo(fixture.operator().getUserId());
        assertThat(withdrawnRevision.getWithdrawalReason()).isEqualTo(WITHDRAWAL_REASON);
        assertThat(unchangedContent.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(unchangedContent.getPublishAt()).isEqualTo(ORIGINAL_PUBLISH_AT);
        assertThat(unchangedContent.getVersionNo()).isEqualTo(originalVersion);
        assertSuccessfulAudit(fixture, withdrawnRevision.getWithdrawnAt());
    }

    @Test
    void withdrawContentRevision_whenPrePublicationRevisionIsValid_keepsOriginalPending() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, CANDIDATE_PUBLISH_AT);
        int originalVersion = fixture.content().getVersionNo();

        performWithdraw(
            fixture.operator(),
            fixture.revision().getContentRevisionId().toString(),
            "{\"reason\":\"" + WITHDRAWAL_REASON + "\"}"
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("EDIT_WITHDRAWN"));

        entityManager.flush();
        entityManager.clear();
        Content unchangedContent = contentRepository.findById(fixture.content().getContentId()).orElseThrow();
        ContentRevision withdrawnRevision = contentRevisionRepository.findById(
            fixture.revision().getContentRevisionId()
        ).orElseThrow();
        assertThat(unchangedContent.getStatus()).isEqualTo(ContentStatus.PENDING);
        assertThat(unchangedContent.getPublishAt()).isEqualTo(ORIGINAL_PUBLISH_AT);
        assertThat(unchangedContent.getVersionNo()).isEqualTo(originalVersion);
        assertThat(withdrawnRevision.getPublishAt()).isEqualTo(CANDIDATE_PUBLISH_AT);
        assertThat(withdrawnRevision.getStatus()).isEqualTo(ContentRevisionStatus.EDIT_WITHDRAWN);
        assertSuccessfulAudit(fixture, withdrawnRevision.getWithdrawnAt());
    }

    @Test
    void withdrawContentRevision_whenRevisionIsAlreadyWithdrawn_returnsStoredResultWithoutNewAudit()
        throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null);
        performWithdraw(
            fixture.operator(),
            fixture.revision().getContentRevisionId().toString(),
            "{\"reason\":\"" + WITHDRAWAL_REASON + "\"}"
        ).andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        ContentRevision firstWithdrawal = contentRevisionRepository.findById(
            fixture.revision().getContentRevisionId()
        ).orElseThrow();
        Instant firstWithdrawnAt = firstWithdrawal.getWithdrawnAt();

        performWithdraw(
            fixture.operator(),
            fixture.revision().getContentRevisionId().toString(),
            "{\"reason\":\"different reason\"}"
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("EDIT_WITHDRAWN"))
            .andExpect(jsonPath("$.data.withdrawalReason").value(WITHDRAWAL_REASON));

        entityManager.flush();
        entityManager.clear();
        ContentRevision repeatedWithdrawal = contentRevisionRepository.findById(
            fixture.revision().getContentRevisionId()
        ).orElseThrow();
        assertThat(repeatedWithdrawal.getWithdrawalReason()).isEqualTo(WITHDRAWAL_REASON);
        assertThat(repeatedWithdrawal.getWithdrawnAt()).isEqualTo(firstWithdrawnAt);
        assertThat(auditEventRepository.count()).isEqualTo(1);
    }

    @Test
    void withdrawContentRevision_whenOperatorIsDifferent_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null);
        AppUser otherOperator = saveUser("other-operator", AppUserStatus.ACTIVE);
        assignOperator(otherOperator, fixture.region());

        performWithdraw(
            otherOperator,
            fixture.revision().getContentRevisionId().toString(),
            "{\"reason\":\"" + WITHDRAWAL_REASON + "\"}"
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertUnchanged(fixture);
    }

    @Test
    void withdrawContentRevision_whenUserHasNoOperatorRole_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null);
        AppUser userWithoutOperatorRole = saveUser("plain-user", AppUserStatus.ACTIVE);

        performWithdraw(
            userWithoutOperatorRole,
            fixture.revision().getContentRevisionId().toString(),
            "{\"reason\":\"" + WITHDRAWAL_REASON + "\"}"
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertUnchanged(fixture);
    }

    @Test
    void withdrawContentRevision_whenOperatorIsInactive_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null);
        AppUser inactiveOperator = saveUser("inactive-operator", AppUserStatus.WITHDRAWING);
        assignOperator(inactiveOperator, fixture.region());

        performWithdraw(
            inactiveOperator,
            fixture.revision().getContentRevisionId().toString(),
            "{\"reason\":\"" + WITHDRAWAL_REASON + "\"}"
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertUnchanged(fixture);
    }

    @Test
    void withdrawContentRevision_whenRevisionDoesNotExist_returnsNotFound() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null);

        performWithdraw(
            fixture.operator(),
            "999999999",
            "{\"reason\":\"" + WITHDRAWAL_REASON + "\"}"
        )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertUnchanged(fixture);
    }

    @Test
    void withdrawContentRevision_whenRevisionIsAlreadyApproved_returnsContentStateConflict() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null);
        fixture.revision().approve(fixture.operator(), Instant.parse("2026-08-02T00:00:00Z"));
        contentRevisionRepository.flush();

        performWithdraw(
            fixture.operator(),
            fixture.revision().getContentRevisionId().toString(),
            "{\"reason\":\"" + WITHDRAWAL_REASON + "\"}"
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void withdrawContentRevision_whenInputIsInvalid_returnsContractErrors() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, null);

        for (String invalidRevisionId : new String[]{"0", "01", "+1", "not-a-number", "9223372036854775808"}) {
            performWithdraw(fixture.operator(), invalidRevisionId, "{\"reason\":\"" + WITHDRAWAL_REASON + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        performWithdraw(
            fixture.operator(),
            fixture.revision().getContentRevisionId().toString(),
            "{\"reason\":\"   \"}"
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        performWithdraw(
            fixture.operator(),
            fixture.revision().getContentRevisionId().toString(),
            "{"
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));

        assertUnchanged(fixture);
    }

    @Test
    void withdrawContentRevision_withoutAuthentication_returnsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/operator/content-revisions/1/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + WITHDRAWAL_REASON + "\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private void assertSuccessfulAudit(Fixture fixture, Instant withdrawnAt) {
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getRegion().getRegionId()).isEqualTo(fixture.region().getRegionId());
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT);
            assertThat(auditEvent.getTargetId()).isEqualTo(fixture.content().getContentId());
            assertThat(auditEvent.getPreviousState()).isEqualTo("EDIT_REQUESTED");
            assertThat(auditEvent.getNextState()).isEqualTo("EDIT_WITHDRAWN");
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(auditEvent.getReasonCode()).isNull();
            assertThat(auditEvent.getOccurredAt()).isEqualTo(withdrawnAt);
            assertThat(auditEvent.getActorRole()).isEqualTo("OPERATOR");
            assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                .hasValueSatisfying(actorLink ->
                    assertThat(actorLink.getActor().getUserId()).isEqualTo(fixture.operator().getUserId())
                );
        });
    }

    private void assertUnchanged(Fixture fixture) {
        assertThat(fixture.revision().getStatus()).isEqualTo(ContentRevisionStatus.EDIT_REQUESTED);
        assertThat(fixture.revision().getWithdrawnAt()).isNull();
        assertThat(fixture.revision().getWithdrawnBy()).isNull();
        assertThat(fixture.revision().getWithdrawalReason()).isNull();
        assertThat(auditEventRepository.count()).isZero();
    }

    private ResultActions performWithdraw(AppUser user, String revisionId, String requestBody) throws Exception {
        return mockMvc.perform(post(
            "/api/v1/operator/content-revisions/{revisionId}/withdraw",
            revisionId
        )
            .header("Authorization", "Bearer " + jwtAccessTokenService.issue(user.getUserId()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody));
    }

    private Fixture createFixture(ContentStatus contentStatus, Instant candidatePublishAt) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = saveRegion("R" + suffix);
        AppUser operator = saveUser("operator-" + suffix, AppUserStatus.ACTIVE);
        assignOperator(operator, region);
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            contentStatus,
            "original title",
            "original description",
            "original location",
            "original operating hours",
            "055-1234-5678",
            "original precautions",
            "age 7+",
            "comfortable clothes",
            "original cancellation policy",
            ORIGINAL_PUBLISH_AT
        ));
        ContentRevision revision = contentRevisionRepository.saveAndFlush(new ContentRevision(
            content,
            1,
            content.getVersionNo(),
            operator,
            ContentRevisionStatus.EDIT_REQUESTED,
            "candidate title",
            "candidate description",
            "candidate location",
            "candidate operating hours",
            "055-9876-5432",
            "candidate precautions",
            "age 8+",
            "walking shoes",
            "candidate cancellation policy",
            candidatePublishAt,
            Instant.parse("2026-08-01T00:00:00Z"),
            null,
            null,
            null,
            null,
            null,
            null
        ));
        return new Fixture(region, operator, content, revision);
    }

    private Region saveRegion(String codePrefix) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return regionRepository.saveAndFlush(new Region(codePrefix + suffix, codePrefix + " region", true));
    }

    private AppUser saveUser(String identifierPrefix, AppUserStatus status) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            identifierPrefix + suffix + "@example.com",
            "hashed-password",
            "user",
            "010-1234-5678",
            status
        ));
    }

    private void assignOperator(AppUser user, Region region) {
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.OPERATOR, region));
    }

    private record Fixture(
        Region region,
        AppUser operator,
        Content content,
        ContentRevision revision
    ) {
    }
}
