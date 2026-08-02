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
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
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
class ContentApprovalControllerIntegrationTest {

    private static final Instant PUBLISH_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-01T00:00:00Z");

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentLogRepository contentLogRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;

    @Autowired
    ContentApprovalControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
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
        this.contentSessionRepository = contentSessionRepository;
        this.contentLogRepository = contentLogRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.entityManager = entityManager;
    }

    @Test
    void approveContent_whenInitialReviewIsValid_approvesContentSessionsAndRecordsAudit() throws Exception {
        Fixture fixture = createPendingFixture(2);

        performApprove(fixture.admin(), fixture.content().getContentId().toString())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 승인에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(fixture.content().getContentId()))
            .andExpect(jsonPath("$.data.status").value("APPROVED"))
            .andExpect(jsonPath("$.data.publishAt").value(PUBLISH_AT.toString()))
            .andExpect(jsonPath("$.data.approvedAt").isString());

        assertApproved(fixture);
    }

    @Test
    void approveContent_whenSessionIsMissing_rollsBackAllChanges() throws Exception {
        Fixture fixture = createPendingFixture(0);

        performApprove(fixture.admin(), fixture.content().getContentId().toString())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertUnchanged(fixture.content().getContentId(), ContentStatus.PENDING, 1);
    }

    @Test
    void approveContent_whenAnySessionIsNotPending_rollsBackAllChanges() throws Exception {
        Fixture fixture = createPendingFixture(2);
        ContentSession rejectedSession = fixture.sessions().getFirst();
        rejectedSession.reject(fixture.admin(), SUBMITTED_AT.plusSeconds(60), "회차 운영 정보 미비");
        contentSessionRepository.flush();

        performApprove(fixture.admin(), fixture.content().getContentId().toString())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertUnchanged(fixture.content().getContentId(), ContentStatus.PENDING, 1);
        assertThat(rejectedSession.getStatus()).isEqualTo(ContentSessionStatus.REJECTED);
        assertThat(fixture.sessions().get(1).getStatus()).isEqualTo(ContentSessionStatus.PENDING);
    }

    @Test
    void approveContent_whenAdminRegionDiffers_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createPendingFixture(1);
        Region otherRegion = saveRegion("OTHER");
        AppUser otherAdmin = saveUser("other-admin", AppUserStatus.ACTIVE);
        assignRegionAdmin(otherAdmin, otherRegion);

        performApprove(otherAdmin, fixture.content().getContentId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertUnchanged(fixture.content().getContentId(), ContentStatus.PENDING, 1);
    }

    @Test
    void approveContent_whenUserIsNotRegionAdmin_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createPendingFixture(1);
        AppUser visitor = saveUser("visitor", AppUserStatus.ACTIVE);

        performApprove(visitor, fixture.content().getContentId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertUnchanged(fixture.content().getContentId(), ContentStatus.PENDING, 1);
    }

    @Test
    void approveContent_whenPrePublicationRevisionIsPending_returnsConflictWithoutChanges() throws Exception {
        Fixture fixture = createPendingFixture(1);
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

        performApprove(fixture.admin(), fixture.content().getContentId().toString())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertUnchanged(fixture.content().getContentId(), ContentStatus.PENDING, 3);
    }

    @Test
    void approveContent_whenContentStateIsNotPendingOrApproved_returnsConflict() throws Exception {
        Fixture fixture = createFixture(ContentStatus.REJECTED, 1);

        performApprove(fixture.admin(), fixture.content().getContentId().toString())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));

        assertUnchanged(fixture.content().getContentId(), ContentStatus.REJECTED, 1);
    }

    @Test
    void approveContent_whenApprovalIsRequestedAgain_returnsSameResultWithoutNewRecords() throws Exception {
        Fixture fixture = createPendingFixture(1);

        performApprove(fixture.admin(), fixture.content().getContentId().toString())
            .andExpect(status().isOk());
        ContentLog firstApprovedLog = contentLogRepository
            .findTopByContentContentIdAndStatusOrderByDateDescIdDesc(
                fixture.content().getContentId(),
                ContentLogStatus.APPROVED
            ).orElseThrow();

        performApprove(fixture.admin(), fixture.content().getContentId().toString())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.approvedAt").value(firstApprovedLog.getDate().toString()));

        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(
            fixture.content().getContentId()
        )).hasSize(2);
        assertThat(auditEventRepository.count()).isEqualTo(1);
        assertThat(fixture.sessions().getFirst().getStatus()).isEqualTo(ContentSessionStatus.SCHEDULED);
    }

    @Test
    void approveContent_whenContentIsMissingOrSoftDeleted_returnsNotFound() throws Exception {
        Fixture fixture = createPendingFixture(1);
        fixture.content().softDelete();
        contentRepository.flush();

        performApprove(fixture.admin(), fixture.content().getContentId().toString())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        performApprove(fixture.admin(), "999999999")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void approveContent_whenContentIdIsInvalid_returnsInputContractErrors() throws Exception {
        Fixture fixture = createPendingFixture(1);

        for (String invalidContentId : new String[]{"0", "-1", "01", "+1"}) {
            performApprove(fixture.admin(), invalidContentId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        for (String invalidTypeContentId : new String[]{"not-a-number", "9223372036854775808"}) {
            performApprove(fixture.admin(), invalidTypeContentId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        }

        assertUnchanged(fixture.content().getContentId(), ContentStatus.PENDING, 1);
    }

    @Test
    void approveContent_withoutAuthentication_returnsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/region-admin/contents/1/approve"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private void assertApproved(Fixture fixture) {
        entityManager.flush();
        entityManager.clear();
        Content approvedContent = contentRepository.findById(fixture.content().getContentId()).orElseThrow();
        List<ContentSession> approvedSessions = contentSessionRepository
            .findByContentContentIdOrderByStartsAtAscSessionIdAsc(approvedContent.getContentId());
        List<ContentLog> logs = contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(
            approvedContent.getContentId()
        );

        assertThat(approvedContent.getStatus()).isEqualTo(ContentStatus.APPROVED);
        assertThat(approvedContent.getPublishAt()).isEqualTo(PUBLISH_AT);
        assertThat(approvedSessions).hasSize(2).allSatisfy(session -> {
            assertThat(session.getStatus()).isEqualTo(ContentSessionStatus.SCHEDULED);
            assertThat(session.getReviewedByUser().getUserId()).isEqualTo(fixture.admin().getUserId());
            assertThat(session.getReviewedAt()).isNotNull();
        });
        assertThat(logs).extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PENDING, ContentLogStatus.APPROVED);
        ContentLog approvedLog = logs.get(1);
        assertThat(approvedLog.getActor().getUserId()).isEqualTo(fixture.admin().getUserId());
        assertThat(approvedSessions).allSatisfy(session ->
            assertThat(session.getReviewedAt()).isEqualTo(approvedLog.getDate())
        );

        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getRegion().getRegionId()).isEqualTo(fixture.region().getRegionId());
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT);
            assertThat(auditEvent.getTargetId()).isEqualTo(approvedContent.getContentId());
            assertThat(auditEvent.getPreviousState()).isEqualTo("PENDING");
            assertThat(auditEvent.getNextState()).isEqualTo("APPROVED");
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(auditEvent.getReasonCode()).isNull();
            assertThat(auditEvent.getOccurredAt()).isEqualTo(approvedLog.getDate());
            assertThat(auditEvent.getActorRole()).isEqualTo("REGION_ADMIN");
            assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                .hasValueSatisfying(actorLink ->
                    assertThat(actorLink.getActor().getUserId()).isEqualTo(fixture.admin().getUserId())
                );
        });
    }

    private void assertUnchanged(
        Long contentId,
        ContentStatus expectedStatus,
        int expectedLogCount
    ) {
        assertThat(contentRepository.findById(contentId)).hasValueSatisfying(content ->
            assertThat(content.getStatus()).isEqualTo(expectedStatus)
        );
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(contentId))
            .hasSize(expectedLogCount);
        assertThat(auditEventRepository.count()).isZero();
    }

    private ResultActions performApprove(AppUser user, String contentId) throws Exception {
        return mockMvc.perform(post(
            "/api/v1/region-admin/contents/{contentId}/approve",
            contentId
        ).header("Authorization", "Bearer " + jwtAccessTokenService.issue(user.getUserId())));
    }

    private Fixture createPendingFixture(int sessionCount) {
        return createFixture(ContentStatus.PENDING, sessionCount);
    }

    private Fixture createFixture(ContentStatus contentStatus, int sessionCount) {
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
            initialLogStatus == ContentLogStatus.REJECTED ? "심사 반려" : null,
            SUBMITTED_AT
        ));
        List<ContentSession> sessions = java.util.stream.IntStream.range(0, sessionCount)
            .mapToObj(index -> contentSessionRepository.saveAndFlush(new ContentSession(
                content,
                region,
                Instant.parse("2026-08-10T01:00:00Z").plusSeconds(index * 7_200L),
                Instant.parse("2026-08-10T03:00:00Z").plusSeconds(index * 7_200L),
                Instant.parse("2026-08-10T00:30:00Z").plusSeconds(index * 7_200L),
                Instant.parse("2026-08-10T02:30:00Z").plusSeconds(index * 7_200L),
                20
            )))
            .toList();
        return new Fixture(region, admin, content, sessions);
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
        List<ContentSession> sessions
    ) {
    }
}
