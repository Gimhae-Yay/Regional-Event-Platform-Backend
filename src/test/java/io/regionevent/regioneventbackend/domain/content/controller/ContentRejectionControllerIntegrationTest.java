package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

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
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
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
class ContentRejectionControllerIntegrationTest {

    private static final Instant PUBLISH_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final String REJECT_REASON = "필수 콘텐츠 정보를 보완해 주세요.";

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentLogRepository contentLogRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;

    @Autowired
    ContentRejectionControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
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
        this.contentRepository = contentRepository;
        this.contentLogRepository = contentLogRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.entityManager = entityManager;
    }

    @Test
    void rejectContent_whenInitialReviewIsValid_rejectsAndRecordsAudit() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, null);

        performReject(
            fixture.admin(),
            fixture.content().getContentId().toString(),
            "{\"reason\":\"  " + REJECT_REASON + "  \"}"
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 반려에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(fixture.content().getContentId()))
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andExpect(jsonPath("$.data.rejectedAt").isString());

        assertRejected(fixture, REJECT_REASON);
    }

    @Test
    void rejectContent_whenAdminRegionDiffers_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, null);
        AppUser otherAdmin = saveUser("other-admin", AppUserStatus.ACTIVE);
        assignRegionAdmin(otherAdmin, saveRegion("OTHER"));

        performReject(otherAdmin, fixture.content().getContentId().toString(), requestBody(REJECT_REASON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertUnchanged(fixture.content().getContentId(), ContentStatus.PENDING, 1);
    }

    @Test
    void rejectContent_whenUserIsNotRegionAdmin_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, null);
        AppUser visitor = saveUser("visitor", AppUserStatus.ACTIVE);

        performReject(visitor, fixture.content().getContentId().toString(), requestBody(REJECT_REASON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertUnchanged(fixture.content().getContentId(), ContentStatus.PENDING, 1);
    }

    @Test
    void rejectContent_whenPrePublicationRevisionIsPending_returnsConflictWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, null);
        contentLogRepository.saveAndFlush(new ContentLog(
            fixture.content(),
            fixture.admin(),
            ContentLogStatus.APPROVED,
            null,
            SUBMITTED_AT.plusSeconds(60)
        ));
        contentLogRepository.saveAndFlush(new ContentLog(
            fixture.content(),
            fixture.admin(),
            ContentLogStatus.PENDING,
            null,
            SUBMITTED_AT.plusSeconds(120)
        ));

        performReject(fixture.admin(), fixture.content().getContentId().toString(), requestBody(REJECT_REASON))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertUnchanged(fixture.content().getContentId(), ContentStatus.PENDING, 3);
    }

    @Test
    void rejectContent_whenSameReasonIsRetried_returnsExistingResultWithoutNewRecords() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, null);

        performReject(fixture.admin(), fixture.content().getContentId().toString(), requestBody(REJECT_REASON))
            .andExpect(status().isOk());
        ContentLog rejectedLog = contentLogRepository.findTopByContentContentIdAndStatusOrderByDateDescIdDesc(
            fixture.content().getContentId(),
            ContentLogStatus.REJECTED
        ).orElseThrow();

        performReject(
            fixture.admin(),
            fixture.content().getContentId().toString(),
            requestBody("  " + REJECT_REASON + "  ")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rejectedAt").value(rejectedLog.getDate().toString()));

        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(
            fixture.content().getContentId()
        )).hasSize(2);
        assertThat(auditEventRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectContent_whenDifferentReasonIsRetried_returnsConflictWithoutNewRecords() throws Exception {
        Fixture fixture = createFixture(ContentStatus.REJECTED, REJECT_REASON);

        performReject(fixture.admin(), fixture.content().getContentId().toString(), requestBody("다른 반려 사유"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertUnchanged(fixture.content().getContentId(), ContentStatus.REJECTED, 1);
    }

    @Test
    void rejectContent_whenContentStateIsNotPending_returnsConflictWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.APPROVED, null);

        performReject(fixture.admin(), fixture.content().getContentId().toString(), requestBody(REJECT_REASON))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertUnchanged(fixture.content().getContentId(), ContentStatus.APPROVED, 1);
    }

    @Test
    void rejectContent_whenContentIsMissingOrSoftDeleted_returnsNotFound() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, null);
        fixture.content().softDelete();
        contentRepository.flush();

        performReject(fixture.admin(), fixture.content().getContentId().toString(), requestBody(REJECT_REASON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        performReject(fixture.admin(), "999999999", requestBody(REJECT_REASON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void rejectContent_whenInputIsInvalid_returnsContractErrors() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, null);

        for (String invalidContentId : new String[]{"0", "-1", "01", "+1"}) {
            performReject(fixture.admin(), invalidContentId, requestBody(REJECT_REASON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        for (String invalidContentId : new String[]{"not-a-number", "9223372036854775808"}) {
            performReject(fixture.admin(), invalidContentId, requestBody(REJECT_REASON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        }
        performReject(fixture.admin(), fixture.content().getContentId().toString(), "{\"reason\":\"   \"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        performReject(fixture.admin(), fixture.content().getContentId().toString(), "{")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));

        assertUnchanged(fixture.content().getContentId(), ContentStatus.PENDING, 1);
    }

    @Test
    void rejectContent_withoutAuthentication_returnsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/region-admin/contents/1/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(REJECT_REASON)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private void assertRejected(Fixture fixture, String reason) {
        entityManager.flush();
        entityManager.clear();
        Content rejectedContent = contentRepository.findById(fixture.content().getContentId()).orElseThrow();
        List<ContentLog> logs = contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(
            rejectedContent.getContentId()
        );

        assertThat(rejectedContent.getStatus()).isEqualTo(ContentStatus.REJECTED);
        assertThat(logs).extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PENDING, ContentLogStatus.REJECTED);
        ContentLog rejectedLog = logs.get(1);
        assertThat(rejectedLog.getActor().getUserId()).isEqualTo(fixture.admin().getUserId());
        assertThat(rejectedLog.getReason()).isEqualTo(reason);

        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getRegion().getRegionId()).isEqualTo(fixture.region().getRegionId());
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT);
            assertThat(auditEvent.getTargetId()).isEqualTo(rejectedContent.getContentId());
            assertThat(auditEvent.getPreviousState()).isEqualTo("PENDING");
            assertThat(auditEvent.getNextState()).isEqualTo("REJECTED");
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(auditEvent.getReasonCode()).isNull();
            assertThat(auditEvent.getOccurredAt()).isEqualTo(rejectedLog.getDate());
            assertThat(auditEvent.getActorRole()).isEqualTo("REGION_ADMIN");
            assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                .hasValueSatisfying(actorLink ->
                    assertThat(actorLink.getActor().getUserId()).isEqualTo(fixture.admin().getUserId())
                );
        });
    }

    private void assertUnchanged(Long contentId, ContentStatus expectedStatus, int expectedLogCount) {
        assertThat(contentRepository.findById(contentId)).hasValueSatisfying(content ->
            assertThat(content.getStatus()).isEqualTo(expectedStatus)
        );
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(contentId))
            .hasSize(expectedLogCount);
        assertThat(auditEventRepository.count()).isZero();
    }

    private ResultActions performReject(AppUser user, String contentId, String requestBody) throws Exception {
        return mockMvc.perform(post(
            "/api/v1/region-admin/contents/{contentId}/reject",
            contentId
        )
            .header("Authorization", "Bearer " + jwtAccessTokenService.issue(user.getUserId()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody));
    }

    private Fixture createFixture(ContentStatus contentStatus, String initialRejectReason) {
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
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            PUBLISH_AT
        ));
        ContentLogStatus initialLogStatus = ContentLogStatus.valueOf(contentStatus.name());
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            operator,
            initialLogStatus,
            initialRejectReason,
            SUBMITTED_AT
        ));
        return new Fixture(region, admin, content);
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

    private String requestBody(String reason) {
        return "{\"reason\":\"" + reason + "\"}";
    }

    private record Fixture(
        Region region,
        AppUser admin,
        Content content
    ) {
    }
}
