package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContentSessionControllerIntegrationTest {

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final AuditEventRepository auditEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    @Autowired
    ContentSessionControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        AuditEventRepository auditEventRepository,
        JdbcTemplate jdbcTemplate,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.auditEventRepository = auditEventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    @Test
    void 회차_예약정보_조회_공개_예정_회차_예약정보를_반환하고_데이터를_변경하지_않는다() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, 2, 60);

        mockMvc.perform(get("/api/v1/sessions/{sessionId}", fixture.session().getSessionId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("회차 예약 정보 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.sessionId").value(fixture.session().getSessionId().toString()))
            .andExpect(jsonPath("$.data.contentId").value(fixture.content().getContentId().toString()))
            .andExpect(jsonPath("$.data.startsAt").value(endsWith("+09:00")))
            .andExpect(jsonPath("$.data.endsAt").value(endsWith("+09:00")))
            .andExpect(jsonPath("$.data.price").value(0))
            .andExpect(jsonPath("$.data.remainingCapacity").value(2))
            .andExpect(jsonPath("$.data.reservable").value(true));

        entityManager.clear();
        ContentSession foundSession = contentSessionRepository.findById(fixture.session().getSessionId()).orElseThrow();
        assertThat(foundSession.getRemainingCapacity()).isEqualTo(2);
        assertThat(capacityHoldRepository.count()).isZero();
        assertThat(reservationRepository.count()).isZero();
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void 회차_예약정보_조회_잔여_정원이_없으면_예약_불가를_반환한다() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, 1, 60);
        jdbcTemplate.update(
            "UPDATE content_session SET remaining_capacity = 0 WHERE session_id = ?",
            fixture.session().getSessionId()
        );
        entityManager.clear();

        mockMvc.perform(get("/api/v1/sessions/{sessionId}", fixture.session().getSessionId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.remainingCapacity").value(0))
            .andExpect(jsonPath("$.data.reservable").value(false));
    }

    @Test
    void 회차_예약정보_조회_MySQL_현재_시각이_시작_시각과_같거나_지난_경우_예약_불가를_반환한다() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, 1, 60);
        jdbcTemplate.update(
            "UPDATE content_session SET starts_at = CURRENT_TIMESTAMP WHERE session_id = ?",
            fixture.session().getSessionId()
        );
        entityManager.clear();

        mockMvc.perform(get("/api/v1/sessions/{sessionId}", fixture.session().getSessionId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reservable").value(false));
    }

    @Test
    void 회차_예약정보_조회_비공개_콘텐츠의_회차는_찾을수없음을_반환한다() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, 1, 60);

        expectNotFound(fixture.session().getSessionId());
    }

    @Test
    void 회차_예약정보_조회_SCHEDULED가_아닌_회차는_찾을수없음을_반환한다() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, 1, 60);

        jdbcTemplate.update(
            "UPDATE content_session SET status = ?, completed_at = CURRENT_TIMESTAMP WHERE session_id = ?",
            "COMPLETED",
            fixture.session().getSessionId()
        );
        entityManager.clear();

        expectNotFound(fixture.session().getSessionId());
    }

    @Test
    void 회차_예약정보_조회_식별자가_양의_정수가_아니면_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/v1/sessions/not-a-number"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void 공개_콘텐츠_회차_목록_조회_인증없이_SCHEDULED_회차를_시작_시각_순으로_반환한다() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, 2, 90);
        ContentSession earlierSession = createScheduledSession(fixture.content(), 30);
        ContentSession cancelledSession = createScheduledSession(fixture.content(), 60);
        cancelledSession.cancel(
            fixture.content().getOperator(),
            Instant.now(),
            "운영상 회차를 취소했습니다."
        );
        contentSessionRepository.saveAndFlush(cancelledSession);
        entityManager.clear();

        mockMvc.perform(get("/api/v1/contents/{contentId}/sessions", fixture.content().getContentId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 회차 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(fixture.content().getContentId().toString()))
            .andExpect(jsonPath("$.data.sessions[0].sessionId").value(earlierSession.getSessionId().toString()))
            .andExpect(jsonPath("$.data.sessions[0].startsAt").value(endsWith("+09:00")))
            .andExpect(jsonPath("$.data.sessions[0].endsAt").value(endsWith("+09:00")))
            .andExpect(jsonPath("$.data.sessions[1].sessionId").value(fixture.session().getSessionId().toString()))
            .andExpect(jsonPath("$.data.sessions.length()").value(2));
    }

    @Test
    void 공개_콘텐츠_회차_목록_조회_SCHEDULED_회차가_없으면_빈_목록을_반환한다() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, 2, 60);
        jdbcTemplate.update(
            "UPDATE content_session SET status = ?, completed_at = CURRENT_TIMESTAMP WHERE session_id = ?",
            "COMPLETED",
            fixture.session().getSessionId()
        );
        entityManager.clear();

        mockMvc.perform(get("/api/v1/contents/{contentId}/sessions", fixture.content().getContentId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contentId").value(fixture.content().getContentId().toString()))
            .andExpect(jsonPath("$.data.sessions").isEmpty());
    }

    @Test
    void 공개_콘텐츠_회차_목록_조회_비공개_콘텐츠는_찾을수없음을_반환한다() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, 2, 60);

        expectPublicContentSessionsNotFound(fixture.content().getContentId());
    }

    @Test
    void 공개_콘텐츠_회차_목록_조회_식별자가_양의_정수가_아니면_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/contents/0/sessions"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/v1/contents/01/sessions"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/v1/contents/+1/sessions"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/v1/contents/9223372036854775808/sessions"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        mockMvc.perform(get("/api/v1/contents/not-a-number/sessions"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    private void expectNotFound(Long sessionId) throws Exception {
        mockMvc.perform(get("/api/v1/sessions/{sessionId}", sessionId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private void expectPublicContentSessionsNotFound(Long contentId) throws Exception {
        mockMvc.perform(get("/api/v1/contents/{contentId}/sessions", contentId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private ContentSession createScheduledSession(Content content, long startsInMinutes) {
        Instant startsAt = Instant.now().plusSeconds(startsInMinutes * 60);
        ContentSession session = new ContentSession(
            content,
            content.getRegion(),
            startsAt,
            startsAt.plusSeconds(7_200),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(5_400),
            2
        );
        session.approve(content.getOperator(), Instant.now());
        return contentSessionRepository.saveAndFlush(session);
    }

    private Fixture createFixture(
        ContentStatus contentStatus,
        int remainingCapacity,
        long startsInMinutes
    ) {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "operator-" + System.nanoTime() + "@example.com",
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
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
            Instant.now().minusSeconds(60)
        ));
        Instant startsAt = Instant.now().plusSeconds(startsInMinutes * 60);
        ContentSession session = new ContentSession(
            content,
            region,
            startsAt,
            startsAt.plusSeconds(7_200),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(5_400),
            remainingCapacity
        );
        session.approve(operator, Instant.now());
        session = contentSessionRepository.saveAndFlush(session);
        return new Fixture(content, session);
    }

    private record Fixture(Content content, ContentSession session) {
    }
}
