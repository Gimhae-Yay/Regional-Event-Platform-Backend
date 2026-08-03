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
import org.springframework.jdbc.core.JdbcTemplate;
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
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
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
class EndContentReservationsControllerIntegrationTest {

    private static final Instant INITIAL_LOGGED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final int SESSION_CAPACITY = 10;

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentLogRepository contentLogRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    @Autowired
    EndContentReservationsControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        ContentLogRepository contentLogRepository,
        CapacityHoldRepository capacityHoldRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        JwtAccessTokenService jwtAccessTokenService,
        JdbcTemplate jdbcTemplate,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.contentLogRepository = contentLogRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    @Test
    void endContentReservations_whenAllSessionsAreTerminal_endsContentAndInvalidatesActiveHolds() throws Exception {
        Fixture fixture = createFixture(true, false);

        performEnd(fixture.admin(), fixture.content().getContentId().toString())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 예약·노출 종료에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(fixture.content().getContentId().toString()))
            .andExpect(jsonPath("$.data.status").value("ENDED"));

        entityManager.flush();
        entityManager.clear();
        assertThat(contentRepository.findById(fixture.content().getContentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.ENDED));
        List<ContentLog> logs = contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(
            fixture.content().getContentId()
        );
        assertThat(logs).extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED, ContentLogStatus.ENDED);
        ContentLog endedLog = logs.getLast();
        assertThat(endedLog.getActor().getUserId()).isEqualTo(fixture.admin().getUserId());
        assertThat(endedLog.getReason()).isNull();

        assertThat(contentSessionRepository.findById(fixture.firstSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(SESSION_CAPACITY));
        assertThat(contentSessionRepository.findById(fixture.secondSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(SESSION_CAPACITY));

        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getRegion().getRegionId()).isEqualTo(fixture.region().getRegionId());
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT);
            assertThat(auditEvent.getTargetId()).isEqualTo(fixture.content().getContentId());
            assertThat(auditEvent.getPreviousState()).isEqualTo("PUBLISHED");
            assertThat(auditEvent.getNextState()).isEqualTo("ENDED");
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(auditEvent.getReasonCode()).isNull();
            assertThat(auditEvent.getOccurredAt()).isEqualTo(endedLog.getDate());
            assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                .hasValueSatisfying(actorLink ->
                    assertThat(actorLink.getActor().getUserId()).isEqualTo(fixture.admin().getUserId())
                );
        });
    }

    @Test
    void endContentReservations_whenAlreadyEnded_returnsOriginalResultWithoutDuplicatingChanges() throws Exception {
        Fixture fixture = createFixture(true, false);

        performEnd(fixture.admin(), fixture.content().getContentId().toString())
            .andExpect(status().isOk());
        ContentLog endedLog = contentLogRepository.findLatestEnded(fixture.content().getContentId())
            .orElseThrow();

        performEnd(fixture.admin(), fixture.content().getContentId().toString())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ENDED"))
            .andExpect(jsonPath("$.data.endedAt").value(endedLog.getDate().toString()));

        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(
            fixture.content().getContentId()
        )).hasSize(2);
        assertThat(auditEventRepository.count()).isEqualTo(1);
        assertThat(contentSessionRepository.findById(fixture.firstSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(SESSION_CAPACITY));
    }

    @Test
    void endContentReservations_whenScheduledSessionRemains_returnsEndConflictWithoutChanges() throws Exception {
        Fixture fixture = createFixture(false, false);

        performEnd(fixture.admin(), fixture.content().getContentId().toString())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONTENT_END_CONFLICT"));

        assertUnchanged(fixture.content().getContentId());
    }

    @Test
    void endContentReservations_whenRequesterIsNotAssignedRegionAdmin_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(true, false);
        Region otherRegion = saveRegion("OTHER");
        AppUser otherRegionAdmin = saveUser("other-admin", AppUserStatus.ACTIVE);
        assignRegionAdmin(otherRegionAdmin, otherRegion);
        AppUser visitor = saveUser("visitor", AppUserStatus.ACTIVE);

        performEnd(otherRegionAdmin, fixture.content().getContentId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        performEnd(visitor, fixture.content().getContentId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertUnchanged(fixture.content().getContentId());
    }

    @Test
    void endContentReservations_whenContentIsMissingOrPathValueIsInvalid_returnsContractErrors() throws Exception {
        Fixture fixture = createFixture(true, false);

        performEnd(fixture.admin(), "999999999")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        for (String invalidContentId : new String[]{
            "0",
            "-1",
            "01",
            "+1",
            "not-a-number",
            "9223372036854775808"
        }) {
            performEnd(fixture.admin(), invalidContentId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        mockMvc.perform(post("/api/v1/region-admin/contents/{contentId}/end", fixture.content().getContentId()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertUnchanged(fixture.content().getContentId());
    }

    private void assertInvalidated(CapacityHold hold) {
        assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.INVALIDATED);
        assertThat(hold.getInvalidationReason()).isEqualTo("CONTENT_ENDED");
        assertThat(hold.getTerminalAt()).isNotNull();
        assertThat(hold.getCapacityReleasedAt()).isNotNull();
    }

    private void assertUnchanged(Long contentId) {
        assertThat(contentRepository.findById(contentId))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED));
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(contentId))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED);
        assertThat(auditEventRepository.count()).isZero();
    }

    private ResultActions performEnd(AppUser user, String contentId) throws Exception {
        return mockMvc.perform(post(
            "/api/v1/region-admin/contents/{contentId}/end",
            contentId
        ).header("Authorization", "Bearer " + jwtAccessTokenService.issue(user.getUserId())));
    }

    private Fixture createFixture(boolean terminalSessions, boolean createActiveHolds) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        Region region = saveRegion("R" + suffix);
        AppUser admin = saveUser("admin-" + suffix, AppUserStatus.ACTIVE);
        assignRegionAdmin(admin, region);
        AppUser operator = saveUser("operator-" + suffix, AppUserStatus.ACTIVE);
        AppUser visitor = saveUser("visitor-" + suffix, AppUserStatus.ACTIVE);
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            now.minusSeconds(86_400)
        ));
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            operator,
            ContentLogStatus.PUBLISHED,
            null,
            INITIAL_LOGGED_AT
        ));

        ContentSession firstSession = saveSession(content, region, admin, now.plusSeconds(3_600));
        ContentSession secondSession = saveSession(content, region, admin, now.plusSeconds(10_800));
        if (terminalSessions) {
            firstSession.complete(now);
            secondSession.cancel(admin, now, "정상 종료 전 회차 취소");
            firstSession = contentSessionRepository.saveAndFlush(firstSession);
            secondSession = contentSessionRepository.saveAndFlush(secondSession);
        }

        Long firstHoldId = null;
        Long secondHoldId = null;
        if (createActiveHolds) {
            firstHoldId = saveActiveHold(region, firstSession, visitor, 2, now).getHoldId();
            secondHoldId = saveActiveHold(region, secondSession, visitor, 1, now).getHoldId();
        }
        return new Fixture(
            region,
            admin,
            content,
            firstSession.getSessionId(),
            secondSession.getSessionId(),
            firstHoldId,
            secondHoldId
        );
    }

    private ContentSession saveSession(
        Content content,
        Region region,
        AppUser reviewer,
        Instant startsAt
    ) {
        ContentSession session = new ContentSession(
            content,
            region,
            startsAt,
            startsAt.plusSeconds(10_800),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(9_000),
            SESSION_CAPACITY
        );
        session.approve(reviewer, startsAt.minusSeconds(3_600));
        return contentSessionRepository.saveAndFlush(session);
    }

    private CapacityHold saveActiveHold(
        Region region,
        ContentSession session,
        AppUser visitor,
        int quantity,
        Instant now
    ) {
        jdbcTemplate.update(
            "UPDATE content_session SET remaining_capacity = remaining_capacity - ? WHERE session_id = ?",
            quantity,
            session.getSessionId()
        );
        return capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            visitor,
            quantity,
            CapacityHoldStatus.ACTIVE,
            now.plusSeconds(600),
            null,
            null,
            null,
            now
        ));
    }

    private Region saveRegion(String regionCode) {
        return regionRepository.saveAndFlush(new Region(regionCode, regionCode + " 지역", true));
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
        Long firstSessionId,
        Long secondSessionId,
        Long firstHoldId,
        Long secondHoldId
    ) {
    }
}
