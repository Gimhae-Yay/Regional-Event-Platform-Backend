package io.regionevent.regioneventbackend.domain.content.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

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
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ContentSessionReservationInfoMySqlIntegrationTest {

    private static final Instant FIXED_NOW = Instant.parse("2037-08-02T00:00:00Z");
    private static final long SESSION_STARTS_IN_SECONDS = 3_600;
    private static final long SESSION_DURATION_SECONDS = 7_200;
    private static final long CHECKIN_OPEN_BEFORE_SECONDS = 1_800;
    private static final long CHECKIN_CLOSE_BEFORE_SECONDS = 1_800;

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    @Autowired
    ContentSessionReservationInfoMySqlIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        JdbcTemplate jdbcTemplate,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @BeforeTransaction
    void removeContentSoftDeleteStatusConstraint() {
        jdbcTemplate.execute("ALTER TABLE content DROP CHECK ck_content_soft_delete_status");
    }

    @AfterTransaction
    void restoreContentSoftDeleteStatusConstraint() {
        jdbcTemplate.execute("""
            ALTER TABLE content
            ADD CONSTRAINT ck_content_soft_delete_status
            CHECK (
                CASE
                    WHEN deleted_at IS NULL THEN TRUE
                    WHEN status = 'PENDING' THEN TRUE
                    WHEN status = 'APPROVED' THEN TRUE
                    ELSE FALSE
                END = TRUE
            )
            """);
    }

    @AfterEach
    void resetMySqlSessionTimestamp() {
        jdbcTemplate.execute("SET timestamp = DEFAULT");
    }

    @Test
    void 회차_예약정보_조회_MySQL_현재_시각과_시작_시각이_같으면_예약_불가를_반환한다() throws Exception {
        ContentSession session = createScheduledSession();
        fixMySqlSessionTimestamp();
        jdbcTemplate.update(
            "UPDATE content_session SET starts_at = CURRENT_TIMESTAMP WHERE session_id = ?",
            session.getSessionId()
        );
        entityManager.clear();

        mockMvc.perform(get("/api/v1/sessions/{sessionId}", session.getSessionId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reservable").value(false));
    }

    @Test
    void 회차_예약정보_조회_삭제된_공개_콘텐츠의_회차는_찾을수없음을_반환한다() throws Exception {
        ContentSession session = createScheduledSession();
        jdbcTemplate.update(
            "UPDATE content SET deleted_at = CURRENT_TIMESTAMP WHERE content_id = ?",
            session.getContent().getContentId()
        );
        entityManager.clear();

        mockMvc.perform(get("/api/v1/sessions/{sessionId}", session.getSessionId()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 공개_콘텐츠_회차_목록_조회는_공개_지역의_회차를_반환한다() throws Exception {
        ContentSession session = createScheduledSession(true);

        mockMvc.perform(get("/api/v1/contents/{contentId}/sessions", session.getContent().getContentId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contentId").value(session.getContent().getContentId().toString()))
            .andExpect(jsonPath("$.data.sessions[0].sessionId").value(session.getSessionId().toString()));
    }

    @Test
    void 공개_콘텐츠_회차_목록_조회는_비공개_지역의_회차를_노출하지_않는다() throws Exception {
        ContentSession session = createScheduledSession(false);

        mockMvc.perform(get("/api/v1/contents/{contentId}/sessions", session.getContent().getContentId()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 회차_예약정보_조회는_비공개_지역의_회차를_노출하지_않는다() throws Exception {
        ContentSession session = createScheduledSession(false);

        mockMvc.perform(get("/api/v1/sessions/{sessionId}", session.getSessionId()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void publicSessionReservationInfo_returnsContentReservationPrice() throws Exception {
        ContentSession session = createScheduledSession();

        mockMvc.perform(get("/api/v1/sessions/{sessionId}", session.getSessionId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.price").value(20_000));
    }

    private ContentSession createScheduledSession() {
        return createScheduledSession(true);
    }

    private ContentSession createScheduledSession(boolean regionPublic) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", regionPublic));
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "operator-" + suffix + "@example.com",
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
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
            "안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            20_000,
            FIXED_NOW.minusSeconds(60)
        ));
        Instant startsAt = FIXED_NOW.plusSeconds(SESSION_STARTS_IN_SECONDS);
        ContentSession session = new ContentSession(
            content,
            region,
            startsAt,
            startsAt.plusSeconds(SESSION_DURATION_SECONDS),
            startsAt.minusSeconds(CHECKIN_OPEN_BEFORE_SECONDS),
            startsAt.plusSeconds(SESSION_DURATION_SECONDS - CHECKIN_CLOSE_BEFORE_SECONDS),
            1
        );
        session.approve(operator, FIXED_NOW);
        return contentSessionRepository.saveAndFlush(session);
    }

    private void fixMySqlSessionTimestamp() {
        jdbcTemplate.execute("SET timestamp = " + FIXED_NOW.getEpochSecond());
    }

}
