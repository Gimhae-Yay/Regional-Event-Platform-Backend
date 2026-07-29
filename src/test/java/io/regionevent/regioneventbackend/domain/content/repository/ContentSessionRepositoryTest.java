package io.regionevent.regioneventbackend.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
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
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ContentSessionRepositoryTest {

    private static final long CANCELLED_SESSION_ID = 1001L;

    private final ContentSessionRepository contentSessionRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;
    @Autowired
    ContentSessionRepositoryTest(
        ContentSessionRepository contentSessionRepository,
        ContentRepository contentRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager
    ) {
        this.contentSessionRepository = contentSessionRepository;
        this.contentRepository = contentRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 회차의_시간과_정원_속성을_저장한다() {
        Region region = saveRegion();
        AppUser operator = saveUser("operator@example.com");
        Content content = saveContent(region, operator);
        Instant startsAt = Instant.parse("2026-08-02T01:00:00Z");
        Instant endsAt = Instant.parse("2026-08-02T03:00:00Z");
        Instant checkinOpenAt = Instant.parse("2026-08-02T00:30:00Z");
        Instant checkinCloseAt = Instant.parse("2026-08-02T03:30:00Z");

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

        assertThat(foundContentSession.getStatus()).isEqualTo(ContentSessionStatus.SCHEDULED);
        assertThat(foundContentSession.getStartsAt()).isEqualTo(startsAt);
        assertThat(foundContentSession.getEndsAt()).isEqualTo(endsAt);
        assertThat(foundContentSession.getCheckinOpenAt()).isEqualTo(checkinOpenAt);
        assertThat(foundContentSession.getCheckinCloseAt()).isEqualTo(checkinCloseAt);
        assertThat(foundContentSession.getCapacity()).isEqualTo(20);
        assertThat(foundContentSession.getRemainingCapacity()).isEqualTo(20);
        assertThat(foundContentSession.getCancelledAt()).isNull();
        assertThat(foundContentSession.getCancelledByUser()).isNull();
        assertThat(foundContentSession.getCancellationReason()).isNull();
        assertThat(foundContentSession.getCompletedAt()).isNull();
        assertThat(foundContentSession.getVersionNo()).isZero();
        assertThat(foundContentSession.getCreatedAt()).isNotNull();
        assertThat(foundContentSession.getUpdatedAt()).isNotNull();
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
    void 잘못된_시간_순서와_정원을_허용하지_않는다() {
        Region region = saveRegion();
        AppUser operator = saveUser("operator@example.com");
        Content content = saveContent(region, operator);
        Instant startsAt = Instant.parse("2026-08-02T01:00:00Z");
        Instant endsAt = Instant.parse("2026-08-02T03:00:00Z");
        Instant checkinOpenAt = Instant.parse("2026-08-02T00:30:00Z");
        Instant checkinCloseAt = Instant.parse("2026-08-02T03:30:00Z");

        assertThatThrownBy(() -> new ContentSession(
            content,
            region,
            endsAt,
            startsAt,
            checkinOpenAt,
            checkinCloseAt,
            20
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new ContentSession(
            content,
            region,
            startsAt,
            endsAt,
            checkinOpenAt,
            checkinCloseAt,
            0
        )).isInstanceOf(IllegalArgumentException.class);
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

}
