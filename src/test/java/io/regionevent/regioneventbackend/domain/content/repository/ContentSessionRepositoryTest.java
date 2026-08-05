package io.regionevent.regioneventbackend.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class ContentSessionRepositoryTest {

    private static final long CANCELLED_SESSION_ID = 1001L;

    private final ContentSessionRepository contentSessionRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ContentSessionRepositoryTest(
        ContentSessionRepository contentSessionRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.contentSessionRepository = contentSessionRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 회차의_시간과_정원_속성을_저장한다() {
        Region region = saveRegion();
        AppUser operator = saveUser("operator@example.com");
        Content content = saveContent(region, operator);
        Instant startsAt = Instant.parse("2026-08-02T01:00:00Z");
        Instant endsAt = Instant.parse("2026-08-02T03:00:00Z");
        Instant checkinOpenAt = Instant.parse("2026-08-02T00:30:00Z");
        Instant checkinCloseAt = Instant.parse("2026-08-02T02:30:00Z");

        ContentSession contentSession = contentSessionRepository.saveAndFlush(
            new ContentSession(
                content,
                region,
                startsAt,
                endsAt,
                checkinOpenAt,
                checkinCloseAt,
                20
            )
        );

        ContentSession foundContentSession = contentSessionRepository.findById(
            contentSession.getSessionId()
        ).orElseThrow();

        assertThat(foundContentSession.getStatus()).isEqualTo(ContentSessionStatus.PENDING);
        assertThat(foundContentSession.getStartsAt()).isEqualTo(startsAt);
        assertThat(foundContentSession.getEndsAt()).isEqualTo(endsAt);
        assertThat(foundContentSession.getCheckinOpenAt()).isEqualTo(checkinOpenAt);
        assertThat(foundContentSession.getCheckinCloseAt()).isEqualTo(checkinCloseAt);
        assertThat(foundContentSession.getCapacity()).isEqualTo(20);
        assertThat(foundContentSession.getRemainingCapacity()).isEqualTo(20);
        assertThat(foundContentSession.getReviewedAt()).isNull();
        assertThat(foundContentSession.getReviewedByUser()).isNull();
        assertThat(foundContentSession.getRejectReason()).isNull();
        assertThat(foundContentSession.getCancelledAt()).isNull();
        assertThat(foundContentSession.getCancelledByUser()).isNull();
        assertThat(foundContentSession.getCancellationReason()).isNull();
        assertThat(foundContentSession.getCompletedAt()).isNull();
        assertThat(foundContentSession.getVersionNo()).isZero();
        assertThat(foundContentSession.getCreatedAt()).isNotNull();
        assertThat(foundContentSession.getUpdatedAt()).isNotNull();
    }

    @Test
    void 반려된_회차의_필수_심사정보를_저장한다() {
        Region region = saveRegion();
        AppUser operator = saveUser("operator-rejected@example.com");
        AppUser reviewer = saveUser("reviewer@example.com");
        Content content = saveContent(region, operator);
        Instant reviewedAt = Instant.parse("2026-08-01T01:00:00Z");
        ContentSession contentSession = createContentSession(content, region);
        contentSession.reject(reviewer, reviewedAt, "운영 시간이 기준에 맞지 않습니다.");

        contentSessionRepository.saveAndFlush(contentSession);
        entityManager.clear();

        ContentSession foundContentSession = contentSessionRepository.findById(
            contentSession.getSessionId()
        ).orElseThrow();

        assertThat(foundContentSession.getStatus()).isEqualTo(ContentSessionStatus.REJECTED);
        assertThat(foundContentSession.getReviewedAt()).isEqualTo(reviewedAt);
        assertThat(foundContentSession.getReviewedByUser().getUserId()).isEqualTo(reviewer.getUserId());
        assertThat(foundContentSession.getRejectReason()).isEqualTo("운영 시간이 기준에 맞지 않습니다.");
    }

    @Test
    void 반려_상태는_필수_심사정보_없이_저장할_수_없다() {
        Region region = saveRegion();
        AppUser operator = saveUser("operator-invalid-rejected@example.com");
        Content content = saveContent(region, operator);
        ContentSession contentSession = contentSessionRepository.saveAndFlush(
            createContentSession(content, region)
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE content_session SET status = 'REJECTED' WHERE session_id = ?",
            contentSession.getSessionId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 기존_SCHEDULED_회차는_심사정보_없이_종료_전이할_수_있다() {
        Region region = saveRegion();
        AppUser operator = saveUser("operator-legacy-scheduled@example.com");
        Content content = saveContent(region, operator);
        ContentSession contentSession = contentSessionRepository.saveAndFlush(
            createContentSession(content, region)
        );
        jdbcTemplate.update(
            "UPDATE content_session SET status = 'SCHEDULED' WHERE session_id = ?",
            contentSession.getSessionId()
        );
        entityManager.clear();
        ContentSession legacyScheduledSession = contentSessionRepository.findById(
            contentSession.getSessionId()
        ).orElseThrow();

        legacyScheduledSession.complete(Instant.parse("2026-08-03T00:00:00Z"));
        contentSessionRepository.saveAndFlush(legacyScheduledSession);

        assertThat(legacyScheduledSession.getStatus()).isEqualTo(ContentSessionStatus.COMPLETED);
        assertThat(legacyScheduledSession.getReviewedAt()).isNull();
        assertThat(legacyScheduledSession.getReviewedByUser()).isNull();
        assertThat(legacyScheduledSession.getCompletedAt()).isEqualTo(
            Instant.parse("2026-08-03T00:00:00Z")
        );
    }

    @Test
    @Sql("classpath:/sql/fixture/content/content-session-cancelled.sql")
    void 콘텐츠와_지역과_취소_처리자를_지연_로딩으로_매핑한다() {
        entityManager.clear();

        ContentSession foundContentSession = contentSessionRepository.findById(CANCELLED_SESSION_ID).orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(persistenceUnitUtil.isLoaded(foundContentSession, "content")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundContentSession, "region")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundContentSession, "cancelledByUser")).isFalse();
        assertThat(foundContentSession.getContent().getContentId()).isEqualTo(101L);
        assertThat(foundContentSession.getRegion().getRegionId()).isEqualTo(101L);
        assertThat(foundContentSession.getCancelledByUser().getUserId()).isEqualTo(103L);
    }

    @Test
    void 원본_콘텐츠의_현재_회차를_시작시각과_식별자_오름차순으로_조회한다() {
        Region region = saveRegion();
        AppUser operator = saveUser("session-detail-operator@example.com");
        Content content = saveContent(region, operator);
        Content otherContent = saveContent(region, operator);
        Instant earlierStartsAt = Instant.parse("2026-08-02T01:00:00Z");
        Instant laterStartsAt = Instant.parse("2026-08-03T01:00:00Z");
        ContentSession firstAtSameTime = contentSessionRepository.saveAndFlush(
            createContentSession(content, region, earlierStartsAt)
        );
        ContentSession secondAtSameTime = contentSessionRepository.saveAndFlush(
            createContentSession(content, region, earlierStartsAt)
        );
        ContentSession laterSession = contentSessionRepository.saveAndFlush(
            createContentSession(content, region, laterStartsAt)
        );
        contentSessionRepository.saveAndFlush(createContentSession(otherContent, region, earlierStartsAt));

        List<ContentSession> sessions = contentSessionRepository
            .findByContentContentIdOrderByStartsAtAscSessionIdAsc(content.getContentId());

        assertThat(sessions).extracting(ContentSession::getSessionId)
            .containsExactly(
                firstAtSameTime.getSessionId(),
                secondAtSameTime.getSessionId(),
                laterSession.getSessionId()
            );
    }

    @Test
    void 대상_콘텐츠의_SCHEDULED_회차를_시작_시각_오름차순으로_조회한다() {
        Region region = saveRegion();
        AppUser operator = saveUser("operator-public-session@example.com");
        Content targetContent = saveContent(region, operator);
        Content otherContent = saveContent(region, operator);
        Instant reviewedAt = Instant.parse("2026-08-01T00:00:00Z");
        ContentSession laterSession = createContentSession(
            targetContent,
            region,
            Instant.parse("2026-08-03T01:00:00Z")
        );
        ContentSession earlierSession = createContentSession(
            targetContent,
            region,
            Instant.parse("2026-08-02T01:00:00Z")
        );
        ContentSession pendingSession = createContentSession(
            targetContent,
            region,
            Instant.parse("2026-08-04T01:00:00Z")
        );
        ContentSession otherContentSession = createContentSession(
            otherContent,
            region,
            Instant.parse("2026-08-01T01:00:00Z")
        );
        laterSession.approve(operator, reviewedAt);
        earlierSession.approve(operator, reviewedAt);
        otherContentSession.approve(operator, reviewedAt);
        contentSessionRepository.saveAllAndFlush(List.of(
            laterSession,
            earlierSession,
            pendingSession,
            otherContentSession
        ));
        entityManager.clear();

        List<ContentSession> sessions = contentSessionRepository
            .findByContentContentIdAndStatusOrderByStartsAtAsc(
                targetContent.getContentId(),
                ContentSessionStatus.SCHEDULED
            );

        assertThat(sessions)
            .extracting(ContentSession::getSessionId)
            .containsExactly(earlierSession.getSessionId(), laterSession.getSessionId());
    }

    private Region saveRegion() {
        return regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(
            new AppUser(
                loginIdentifier,
                "hashed-password",
                "운영자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            )
        );
    }

    private Content saveContent(Region region, AppUser operator) {
        return contentRepository.saveAndFlush(
            new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.PENDING,
                "김해 가야 문화 체험",
                "김해 가야 문화를 체험하는 행사입니다.",
                "김해문화의전당",
                "매일 10:00~18:00",
                "055-123-4567",
                "안전요원의 안내를 따라주세요.",
                "만 7세 이상",
                "편한 복장",
                "시작 하루 전까지 취소할 수 있습니다.",
                Instant.parse("2026-08-01T00:00:00Z")
            )
        );
    }

    private ContentSession createContentSession(
        Content content,
        Region region
    ) {
        return createContentSession(content, region, Instant.parse("2026-08-02T01:00:00Z"));
    }

    private ContentSession createContentSession(
        Content content,
        Region region,
        Instant startsAt
    ) {
        return new ContentSession(
            content,
            region,
            startsAt,
            startsAt.plusSeconds(7_200),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(5_400),
            20
        );
    }

}
