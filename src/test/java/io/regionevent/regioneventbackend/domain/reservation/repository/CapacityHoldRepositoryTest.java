package io.regionevent.regioneventbackend.domain.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class CapacityHoldRepositoryTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-08-02T00:10:00Z");
    private static final Instant TERMINAL_AT = Instant.parse("2026-08-02T00:05:00Z");
    private static final Instant CAPACITY_RELEASED_AT = Instant.parse("2026-08-02T00:05:01Z");
    private static final Instant REVIEWED_AT = Instant.parse("2026-08-02T00:00:00Z");

    private final CapacityHoldRepository capacityHoldRepository;
    private final RegionRepository regionRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    CapacityHoldRepositoryTest(
        CapacityHoldRepository capacityHoldRepository,
        RegionRepository regionRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.capacityHoldRepository = capacityHoldRepository;
        this.regionRepository = regionRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 홀드의_수량과_상태별_종결_필드를_저장한다() {
        Region region = saveRegion("GIMHAE");
        ContentSession contentSession = saveContentSession(region);
        AppUser user = saveUser("visitor@example.com");

        CapacityHold activeHold = capacityHoldRepository.saveAndFlush(
            newHold(region, contentSession, user, 2, CapacityHoldStatus.ACTIVE, null, null, null)
        );
        CapacityHold consumedHold = capacityHoldRepository.saveAndFlush(
            newHold(region, contentSession, user, 1, CapacityHoldStatus.CONSUMED, TERMINAL_AT, null, null)
        );
        CapacityHold expiredHold = capacityHoldRepository.saveAndFlush(
            newHold(
                region,
                contentSession,
                user,
                3,
                CapacityHoldStatus.EXPIRED,
                TERMINAL_AT,
                CAPACITY_RELEASED_AT,
                null
            )
        );
        CapacityHold invalidatedHold = capacityHoldRepository.saveAndFlush(
            newHold(
                region,
                contentSession,
                null,
                4,
                CapacityHoldStatus.INVALIDATED,
                TERMINAL_AT,
                CAPACITY_RELEASED_AT,
                "회차가 취소되었습니다."
            )
        );

        assertThat(activeHold.getQuantity()).isEqualTo(2);
        assertThat(activeHold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE);
        assertThat(activeHold.getTerminalAt()).isNull();
        assertThat(activeHold.getCapacityReleasedAt()).isNull();
        assertThat(consumedHold.getTerminalAt()).isEqualTo(TERMINAL_AT);
        assertThat(consumedHold.getCapacityReleasedAt()).isNull();
        assertThat(expiredHold.getCapacityReleasedAt()).isEqualTo(CAPACITY_RELEASED_AT);
        assertThat(invalidatedHold.getUser()).isNull();
        assertThat(invalidatedHold.getInvalidationReason()).isEqualTo("회차가 취소되었습니다.");
        assertThat(invalidatedHold.getCreatedAt()).isNotNull();
    }

    @Test
    void 홀드는_양수_수량과_상태에_맞는_종결_필드_및_무효화_사유가_필요하다() {
        Region region = saveRegion("GIMHAE");
        ContentSession contentSession = saveContentSession(region);

        assertThatThrownBy(() -> newHold(
            region,
            contentSession,
            null,
            0,
            CapacityHoldStatus.ACTIVE,
            null,
            null,
            null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newHold(
            region,
            contentSession,
            null,
            1,
            CapacityHoldStatus.INVALIDATED,
            TERMINAL_AT,
            CAPACITY_RELEASED_AT,
            null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newHold(
            region,
            contentSession,
            null,
            1,
            CapacityHoldStatus.INVALIDATED,
            TERMINAL_AT,
            CAPACITY_RELEASED_AT,
            " "
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newHold(
            region,
            contentSession,
            null,
            1,
            CapacityHoldStatus.EXPIRED,
            TERMINAL_AT,
            CAPACITY_RELEASED_AT,
            "회차가 취소되었습니다."
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newHold(
            region,
            contentSession,
            null,
            1,
            CapacityHoldStatus.CONSUMED,
            null,
            null,
            null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newHold(
            region,
            contentSession,
            null,
            1,
            CapacityHoldStatus.EXPIRED,
            TERMINAL_AT,
            null,
            null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 홀드의_지역과_회차_지역은_일치해야_한다() {
        Region gimhae = saveRegion("GIMHAE");
        Region busan = saveRegion("BUSAN");
        ContentSession contentSession = saveContentSession(gimhae);

        assertThatThrownBy(() -> newHold(
            busan,
            contentSession,
            null,
            1,
            CapacityHoldStatus.ACTIVE,
            null,
            null,
            null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 홀드의_지역_회차_사용자_연관관계는_지연_로딩한다() {
        Region region = saveRegion("GIMHAE");
        ContentSession contentSession = saveContentSession(region);
        AppUser user = saveUser("visitor@example.com");
        CapacityHold capacityHold = capacityHoldRepository.saveAndFlush(
            newHold(region, contentSession, user, 1, CapacityHoldStatus.ACTIVE, null, null, null)
        );
        entityManager.clear();

        CapacityHold foundCapacityHold = capacityHoldRepository.findById(capacityHold.getHoldId()).orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(persistenceUnitUtil.isLoaded(foundCapacityHold, "contentSession")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundCapacityHold, "region")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundCapacityHold, "user")).isFalse();
        assertThat(foundCapacityHold.getRegion().getRegionId()).isEqualTo(region.getRegionId());
        assertThat(foundCapacityHold.getContentSession().getSessionId()).isEqualTo(contentSession.getSessionId());
        assertThat(foundCapacityHold.getUser().getUserId()).isEqualTo(user.getUserId());
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\t", "\n", "\r\n"})
    void 무효화_홀드는_네이티브_SQL의_공백_전용_사유를_거부한다(String invalidationReason) {
        Region region = saveRegion("GIMHAE");
        ContentSession contentSession = saveContentSession(region);

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO capacity_hold (
                region_id,
                session_id,
                quantity,
                status,
                expires_at,
                terminal_at,
                invalidation_reason,
                capacity_released_at,
                created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            region.getRegionId(),
            contentSession.getSessionId(),
            1,
            CapacityHoldStatus.INVALIDATED.name(),
            EXPIRES_AT,
            TERMINAL_AT,
            invalidationReason,
            CAPACITY_RELEASED_AT,
            TERMINAL_AT
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private CapacityHold newHold(
        Region region,
        ContentSession contentSession,
        AppUser user,
        int quantity,
        CapacityHoldStatus status,
        Instant terminalAt,
        Instant capacityReleasedAt,
        String invalidationReason
    ) {
        return new CapacityHold(
            region,
            contentSession,
            user,
            quantity,
            status,
            EXPIRES_AT,
            terminalAt,
            invalidationReason,
            capacityReleasedAt
        );
    }

    private Region saveRegion(String regionCode) {
        return regionRepository.saveAndFlush(new Region(regionCode, regionCode + "시", true));
    }

    private ContentSession saveContentSession(Region region) {
        AppUser operator = saveUser("operator-" + region.getRegionCode() + "@example.com");
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
            Instant.parse("2026-08-01T00:00:00Z")
        ));

        ContentSession contentSession = new ContentSession(
            content,
            region,
            Instant.parse("2026-08-02T01:00:00Z"),
            Instant.parse("2026-08-02T03:00:00Z"),
            Instant.parse("2026-08-02T00:30:00Z"),
            Instant.parse("2026-08-02T02:30:00Z"),
            20
        );
        contentSession.approve(operator, REVIEWED_AT);
        return contentSessionRepository.saveAndFlush(contentSession);
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "예약 사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }
}
