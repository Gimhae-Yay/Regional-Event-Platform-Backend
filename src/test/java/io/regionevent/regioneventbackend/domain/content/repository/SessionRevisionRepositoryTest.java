package io.regionevent.regioneventbackend.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class SessionRevisionRepositoryTest {

    private static final Instant STARTS_AT = Instant.parse("2026-08-02T01:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-08-02T03:00:00Z");
    private static final Instant CHECKIN_OPEN_AT = Instant.parse("2026-08-02T00:30:00Z");
    private static final Instant CHECKIN_CLOSE_AT = Instant.parse("2026-08-02T02:30:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant REVIEWED_AT = Instant.parse("2026-08-01T01:00:00Z");

    private final SessionRevisionRepository sessionRevisionRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    SessionRevisionRepositoryTest(
        SessionRevisionRepository sessionRevisionRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.sessionRevisionRepository = sessionRevisionRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 회차_수정_요청의_후보_필드와_심사_전_상태를_저장한다() {
        SessionRevisionFixtures fixtures = createFixtures();

        SessionRevision sessionRevision = sessionRevisionRepository.saveAndFlush(newRevision(
            fixtures,
            SessionRevisionStatus.PENDING,
            null,
            null,
            null
        ));
        entityManager.clear();

        SessionRevision foundSessionRevision = sessionRevisionRepository.findById(
            sessionRevision.getSessionRevisionId()
        ).orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(foundSessionRevision.getBaseSessionVersion()).isEqualTo(fixtures.targetSession().getVersionNo());
        assertThat(foundSessionRevision.getStartsAt()).isEqualTo(STARTS_AT);
        assertThat(foundSessionRevision.getEndsAt()).isEqualTo(ENDS_AT);
        assertThat(foundSessionRevision.getCheckinOpenAt()).isEqualTo(CHECKIN_OPEN_AT);
        assertThat(foundSessionRevision.getCheckinCloseAt()).isEqualTo(CHECKIN_CLOSE_AT);
        assertThat(foundSessionRevision.getCapacity()).isEqualTo(30);
        assertThat(foundSessionRevision.getStatus()).isEqualTo(SessionRevisionStatus.PENDING);
        assertThat(foundSessionRevision.getSubmittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(foundSessionRevision.getReviewedAt()).isNull();
        assertThat(foundSessionRevision.getReviewedBy()).isNull();
        assertThat(foundSessionRevision.getRejectReason()).isNull();
        assertThat(foundSessionRevision.getCreatedAt()).isNotNull();
        assertThat(persistenceUnitUtil.isLoaded(foundSessionRevision, "content")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundSessionRevision, "region")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundSessionRevision, "targetSession")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundSessionRevision, "requestedBy")).isFalse();
        assertThat(foundSessionRevision.getContent().getContentId()).isEqualTo(fixtures.content().getContentId());
        assertThat(foundSessionRevision.getRegion().getRegionId()).isEqualTo(fixtures.region().getRegionId());
        assertThat(foundSessionRevision.getTargetSession().getSessionId()).isEqualTo(fixtures.targetSession().getSessionId());
        assertThat(foundSessionRevision.getRequestedBy().getUserId()).isEqualTo(fixtures.requestedBy().getUserId());
    }

    @Test
    void 승인과_반려_상태에_심사_정보를_저장한다() {
        SessionRevisionFixtures fixtures = createFixtures();

        SessionRevision approvedRevision = sessionRevisionRepository.saveAndFlush(newRevision(
            fixtures,
            SessionRevisionStatus.APPROVED,
            REVIEWED_AT,
            fixtures.reviewedBy(),
            null
        ));
        SessionRevision rejectedRevision = sessionRevisionRepository.saveAndFlush(newRevision(
            fixtures,
            SessionRevisionStatus.REJECTED,
            REVIEWED_AT,
            fixtures.reviewedBy(),
            "체크인 가능 시간을 다시 확인해 주세요."
        ));
        entityManager.clear();

        SessionRevision foundApprovedRevision = sessionRevisionRepository.findById(
            approvedRevision.getSessionRevisionId()
        ).orElseThrow();
        SessionRevision foundRejectedRevision = sessionRevisionRepository.findById(
            rejectedRevision.getSessionRevisionId()
        ).orElseThrow();

        assertThat(foundApprovedRevision.getReviewedAt()).isEqualTo(REVIEWED_AT);
        assertThat(foundApprovedRevision.getReviewedBy().getUserId()).isEqualTo(fixtures.reviewedBy().getUserId());
        assertThat(foundApprovedRevision.getRejectReason()).isNull();
        assertThat(foundRejectedRevision.getReviewedAt()).isEqualTo(REVIEWED_AT);
        assertThat(foundRejectedRevision.getReviewedBy().getUserId()).isEqualTo(fixtures.reviewedBy().getUserId());
        assertThat(foundRejectedRevision.getRejectReason()).isEqualTo("체크인 가능 시간을 다시 확인해 주세요.");
    }

    @Test
    void 심사_상태에_맞지_않는_심사_정보를_허용하지_않는다() {
        SessionRevisionFixtures fixtures = createFixtures();

        assertThatThrownBy(() -> newRevision(
            fixtures,
            SessionRevisionStatus.PENDING,
            REVIEWED_AT,
            fixtures.reviewedBy(),
            null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newRevision(
            fixtures,
            SessionRevisionStatus.APPROVED,
            null,
            null,
            null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newRevision(
            fixtures,
            SessionRevisionStatus.REJECTED,
            REVIEWED_AT,
            fixtures.reviewedBy(),
            "  "
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 데이터베이스가_심사_상태와_대상_회차의_콘텐츠_지역_일치를_강제한다() {
        SessionRevisionFixtures fixtures = createFixtures();

        assertThatThrownBy(() -> insertSessionRevision(
            fixtures.content(),
            fixtures.region(),
            fixtures.targetSession(),
            fixtures.requestedBy(),
            SessionRevisionStatus.PENDING.name(),
            REVIEWED_AT,
            fixtures.reviewedBy(),
            null
        )).isInstanceOf(DataIntegrityViolationException.class);

        Region anotherRegion = saveRegion("BUSAN");
        AppUser anotherOperator = saveUser("busan-operator@example.com");
        Content anotherContent = saveContent(anotherRegion, anotherOperator);

        assertThatThrownBy(() -> insertSessionRevision(
            anotherContent,
            anotherRegion,
            fixtures.targetSession(),
            fixtures.requestedBy(),
            SessionRevisionStatus.PENDING.name(),
            null,
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private SessionRevision newRevision(
        SessionRevisionFixtures fixtures,
        SessionRevisionStatus status,
        Instant reviewedAt,
        AppUser reviewedBy,
        String rejectReason
    ) {
        return new SessionRevision(
            fixtures.content(),
            fixtures.region(),
            fixtures.targetSession(),
            fixtures.targetSession().getVersionNo(),
            STARTS_AT,
            ENDS_AT,
            CHECKIN_OPEN_AT,
            CHECKIN_CLOSE_AT,
            30,
            status,
            fixtures.requestedBy(),
            SUBMITTED_AT,
            reviewedAt,
            reviewedBy,
            rejectReason
        );
    }

    private void insertSessionRevision(
        Content content,
        Region region,
        ContentSession targetSession,
        AppUser requestedBy,
        String status,
        Instant reviewedAt,
        AppUser reviewedBy,
        String rejectReason
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO session_revision (
                    content_id,
                    region_id,
                    target_session_id,
                    base_session_version,
                    starts_at,
                    ends_at,
                    checkin_open_at,
                    checkin_close_at,
                    capacity,
                    status,
                    requested_by_user_id,
                    submitted_at,
                    reviewed_at,
                    reviewed_by_user_id,
                    reject_reason,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            content.getContentId(),
            region.getRegionId(),
            targetSession.getSessionId(),
            targetSession.getVersionNo(),
            Timestamp.from(STARTS_AT),
            Timestamp.from(ENDS_AT),
            Timestamp.from(CHECKIN_OPEN_AT),
            Timestamp.from(CHECKIN_CLOSE_AT),
            30,
            status,
            requestedBy.getUserId(),
            Timestamp.from(SUBMITTED_AT),
            reviewedAt == null ? null : Timestamp.from(reviewedAt),
            reviewedBy == null ? null : reviewedBy.getUserId(),
            rejectReason,
            Timestamp.from(SUBMITTED_AT)
        );
    }

    private SessionRevisionFixtures createFixtures() {
        Region region = saveRegion("GIMHAE");
        AppUser operator = saveUser("operator@example.com");
        Content content = saveContent(region, operator);
        ContentSession targetSession = contentSessionRepository.saveAndFlush(new ContentSession(
            content,
            region,
            STARTS_AT,
            ENDS_AT,
            CHECKIN_OPEN_AT,
            CHECKIN_CLOSE_AT,
            20
        ));
        AppUser requestedBy = saveUser("requester@example.com");
        AppUser reviewedBy = saveUser("reviewer@example.com");

        return new SessionRevisionFixtures(region, content, targetSession, requestedBy, reviewedBy);
    }

    private Region saveRegion(String regionCode) {
        return regionRepository.saveAndFlush(new Region(regionCode, regionCode + "시", true));
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "운영자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private Content saveContent(Region region, AppUser operator) {
        return contentRepository.saveAndFlush(new Content(
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
        ));
    }

    private record SessionRevisionFixtures(
        Region region,
        Content content,
        ContentSession targetSession,
        AppUser requestedBy,
        AppUser reviewedBy
    ) {
    }
}
