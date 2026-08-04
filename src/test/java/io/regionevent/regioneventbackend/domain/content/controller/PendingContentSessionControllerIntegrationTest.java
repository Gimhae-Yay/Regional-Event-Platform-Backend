package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
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
class PendingContentSessionControllerIntegrationTest {

    private final MockMvc mockMvc;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final AuditEventRepository auditEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    @Autowired
    PendingContentSessionControllerIntegrationTest(
        MockMvc mockMvc,
        JwtAccessTokenService jwtAccessTokenService,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        AuditEventRepository auditEventRepository,
        JdbcTemplate jdbcTemplate,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.auditEventRepository = auditEventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    @Test
    void getPendingSessions_returnsOnlyEligibleSessionsInFixedOrderWithoutChanges() throws Exception {
        Region region = saveRegion("PENDING-LIST");
        AppUser admin = saveRegionAdmin("pending-list-admin@example.com", region);
        AppUser operator = saveUser("pending-list-operator@example.com");
        Content approved = saveContent(region, operator, ContentStatus.APPROVED, "승인 콘텐츠");
        Content published = saveContent(region, operator, ContentStatus.PUBLISHED, "공개 콘텐츠");
        Content initialPending = saveContent(region, operator, ContentStatus.PENDING, "최초 심사 콘텐츠");
        Content deleted = saveContent(region, operator, ContentStatus.APPROVED, "삭제 콘텐츠");
        deleted.softDelete(Instant.parse("2026-08-01T00:00:00Z"));
        contentRepository.saveAndFlush(deleted);
        ContentSession later = savePendingSession(approved, region, "2026-08-22T01:00:00Z");
        ContentSession first = savePendingSession(published, region, "2026-08-21T01:00:00Z");
        ContentSession tied = savePendingSession(published, region, "2026-08-21T03:00:00Z");
        savePendingSession(initialPending, region, "2026-08-20T01:00:00Z");
        savePendingSession(deleted, region, "2026-08-20T01:00:00Z");
        ContentSession scheduled = savePendingSession(published, region, "2026-08-23T01:00:00Z");
        scheduled.approve(admin, Instant.now());
        contentSessionRepository.saveAndFlush(scheduled);
        updateCreatedAt(later, "2026-08-02T00:00:00Z");
        updateCreatedAt(first, "2026-08-01T00:00:00Z");
        updateCreatedAt(tied, "2026-08-01T00:00:00Z");
        entityManager.clear();
        DatabaseSnapshot before = snapshot();
        SessionState firstBefore = sessionState(first);
        SessionState tiedBefore = sessionState(tied);
        SessionState laterBefore = sessionState(later);
        ContentState publishedBefore = contentState(published);
        ContentState approvedBefore = contentState(approved);

        mockMvc.perform(get("/api/v1/region-admin/sessions").queryParam("status", "PENDING")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("심사 대기 회차 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.sessions.length()").value(3))
            .andExpect(jsonPath("$.data.sessions[0].sessionId").value(first.getSessionId().toString()))
            .andExpect(jsonPath("$.data.sessions[0].contentId").value(published.getContentId().toString()))
            .andExpect(jsonPath("$.data.sessions[0].contentTitle").value("공개 콘텐츠"))
            .andExpect(jsonPath("$.data.sessions[0].status").value("PENDING"))
            .andExpect(jsonPath("$.data.sessions[0].startsAt").value("2026-08-21T10:00:00+09:00"))
            .andExpect(jsonPath("$.data.sessions[0].endsAt").value("2026-08-21T12:00:00+09:00"))
            .andExpect(jsonPath("$.data.sessions[0].checkinOpenAt").value("2026-08-21T09:30:00+09:00"))
            .andExpect(jsonPath("$.data.sessions[0].checkinCloseAt").value("2026-08-21T11:30:00+09:00"))
            .andExpect(jsonPath("$.data.sessions[0].capacity").value(20))
            .andExpect(jsonPath("$.data.sessions[0].createdAt").value("2026-08-01T00:00:00Z"))
            .andExpect(jsonPath("$.data.sessions[0].operator.operatorId").value(operator.getUserId().toString()))
            .andExpect(jsonPath("$.data.sessions[0].operator.name").value("사용자"))
            .andExpect(jsonPath("$.data.sessions[1].sessionId").value(tied.getSessionId().toString()))
            .andExpect(jsonPath("$.data.sessions[2].sessionId").value(later.getSessionId().toString()));

        assertThat(snapshot()).isEqualTo(before);
        assertThat(sessionState(first)).isEqualTo(firstBefore);
        assertThat(sessionState(tied)).isEqualTo(tiedBefore);
        assertThat(sessionState(later)).isEqualTo(laterBefore);
        assertThat(contentState(published)).isEqualTo(publishedBefore);
        assertThat(contentState(approved)).isEqualTo(approvedBefore);
        assertThat(contentSessionRepository.findById(scheduled.getSessionId()))
            .get()
            .satisfies(session -> assertThat(session.getStatus().name()).isEqualTo("SCHEDULED"));
    }

    @Test
    void getPendingSessions_returnsEmptyListWhenRegionHasNoEligibleSession() throws Exception {
        Region region = saveRegion("EMPTY");
        AppUser admin = saveRegionAdmin("empty-admin@example.com", region);

        mockMvc.perform(get("/api/v1/region-admin/sessions").queryParam("status", "PENDING")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessions").isEmpty());
    }

    @Test
    void getPendingSessions_rejectsMissingOrUnsupportedStatus() throws Exception {
        Region region = saveRegion("INVALID");
        AppUser admin = saveRegionAdmin("invalid-admin@example.com", region);

        mockMvc.perform(get("/api/v1/region-admin/sessions")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/region-admin/sessions").queryParam("status", "SCHEDULED")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void getPendingSessions_enforcesRegionAdminAndRegionIsolation() throws Exception {
        Region ownerRegion = saveRegion("OWNER");
        Region otherRegion = saveRegion("OTHER");
        AppUser ownerAdmin = saveRegionAdmin("owner-admin@example.com", ownerRegion);
        AppUser otherAdmin = saveRegionAdmin("other-admin@example.com", otherRegion);
        AppUser visitor = saveUser("visitor@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(visitor, UserRole.VISITOR, null));
        AppUser operator = saveUser("isolation-operator@example.com");
        savePendingSession(saveContent(ownerRegion, operator, ContentStatus.APPROVED, "대상"), ownerRegion,
            "2026-08-21T01:00:00Z");

        mockMvc.perform(get("/api/v1/region-admin/sessions").queryParam("status", "PENDING"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/v1/region-admin/sessions").queryParam("status", "PENDING")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(visitor)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/region-admin/sessions").queryParam("status", "PENDING")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(otherAdmin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessions").isEmpty());
        mockMvc.perform(get("/api/v1/region-admin/sessions").queryParam("status", "PENDING")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(ownerAdmin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessions.length()").value(1));
    }

    private ContentSession savePendingSession(Content content, Region region, String startsAt) {
        Instant start = Instant.parse(startsAt);
        return contentSessionRepository.saveAndFlush(new ContentSession(
            content,
            region,
            start,
            start.plusSeconds(7_200),
            start.minusSeconds(1_800),
            start.plusSeconds(5_400),
            20
        ));
    }

    private Content saveContent(Region region, AppUser operator, ContentStatus status, String title) {
        return contentRepository.saveAndFlush(new Content(
            region, operator, ContentType.EVENT_EXPERIENCE, status, title, "설명", "위치", "운영 시간",
            "055-000-0000", "유의사항", "연령", "준비물", "취소 정책", Instant.parse("2026-08-20T00:00:00Z")
        ));
    }

    private AppUser saveRegionAdmin(String loginIdentifier, Region region) {
        AppUser user = saveUser(loginIdentifier);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.REGION_ADMIN, region));
        return user;
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier, "hashed-password", "사용자", "010-1234-5678", AppUserStatus.ACTIVE
        ));
    }

    private Region saveRegion(String suffix) {
        return regionRepository.saveAndFlush(new Region("REGION-" + suffix, "테스트 지역", true));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private void updateCreatedAt(ContentSession session, String createdAt) {
        jdbcTemplate.update(
            "UPDATE content_session SET created_at = ? WHERE session_id = ?",
            Timestamp.from(Instant.parse(createdAt)),
            session.getSessionId()
        );
    }

    private DatabaseSnapshot snapshot() {
        return new DatabaseSnapshot(contentRepository.count(), contentSessionRepository.count(), auditEventRepository.count());
    }

    private SessionState sessionState(ContentSession session) {
        return jdbcTemplate.queryForObject(
            "SELECT status, version_no, updated_at FROM content_session WHERE session_id = ?",
            (resultSet, rowNum) -> new SessionState(
                resultSet.getString("status"),
                resultSet.getInt("version_no"),
                resultSet.getTimestamp("updated_at").toInstant()
            ),
            session.getSessionId()
        );
    }

    private ContentState contentState(Content content) {
        return jdbcTemplate.queryForObject(
            "SELECT status, version_no, updated_at FROM content WHERE content_id = ?",
            (resultSet, rowNum) -> new ContentState(
                resultSet.getString("status"),
                resultSet.getInt("version_no"),
                resultSet.getTimestamp("updated_at").toInstant()
            ),
            content.getContentId()
        );
    }

    private record DatabaseSnapshot(long contents, long sessions, long auditEvents) {
    }

    private record SessionState(String status, int versionNo, Instant updatedAt) {
    }

    private record ContentState(String status, int versionNo, Instant updatedAt) {
    }
}
