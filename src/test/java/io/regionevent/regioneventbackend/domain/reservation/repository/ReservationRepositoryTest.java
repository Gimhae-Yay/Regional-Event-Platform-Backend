package io.regionevent.regioneventbackend.domain.reservation.repository;

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
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class ReservationRepositoryTest {

    private static final Instant CONFIRMED_AT = Instant.parse("2026-08-02T00:00:00Z");
    private static final Instant TERMINAL_AT = Instant.parse("2026-08-01T23:59:00Z");
    private static final Instant CANCELLED_AT = Instant.parse("2026-08-01T23:59:30Z");
    private static final Instant EXPIRED_AT = Instant.parse("2026-08-02T00:01:00Z");

    private final ReservationRepository reservationRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final RegionRepository regionRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ReservationRepositoryTest(
        ReservationRepository reservationRepository,
        CapacityHoldRepository capacityHoldRepository,
        RegionRepository regionRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.reservationRepository = reservationRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.regionRepository = regionRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 예약의_필수_필드와_홀드_역방향_관계를_저장한다() {
        ReservationFixtures fixtures = createFixtures();
        CapacityHold capacityHold = saveConsumedHold(fixtures);

        Reservation reservation = reservationRepository.saveAndFlush(newReservation(
            "R-20260802-001",
            "qr-reference-001",
            fixtures.region(),
            capacityHold,
            fixtures.contentSession(),
            fixtures.user(),
            ReservationStatus.CONFIRMED,
            null,
            null,
            null
        ));
        entityManager.clear();

        Reservation foundReservation = reservationRepository.findById(reservation.getReservationId()).orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(foundReservation.getReservationNo()).isEqualTo("R-20260802-001");
        assertThat(foundReservation.getQrReference()).isEqualTo("qr-reference-001");
        assertThat(foundReservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(foundReservation.getConfirmedAt()).isEqualTo(CONFIRMED_AT);
        assertThat(foundReservation.getUpdatedAt()).isNotNull();
        assertThat(persistenceUnitUtil.isLoaded(foundReservation, "capacityHold")).isFalse();
        assertThat(foundReservation.getCapacityHold().getHoldId()).isEqualTo(capacityHold.getHoldId());

        CapacityHold foundCapacityHold = capacityHoldRepository.findById(capacityHold.getHoldId()).orElseThrow();
        assertThat(foundCapacityHold.getReservation().getReservationId()).isEqualTo(reservation.getReservationId());
    }

    @Test
    void 예약은_홀드의_지역과_회차와_일치해야_한다() {
        ReservationFixtures fixtures = createFixtures();
        CapacityHold capacityHold = saveConsumedHold(fixtures);
        Region anotherRegion = saveRegion("BUSAN");
        ContentSession anotherContentSession = saveContentSession(anotherRegion);

        assertThatThrownBy(() -> newReservation(
            "R-20260802-001",
            "qr-reference-001",
            anotherRegion,
            capacityHold,
            fixtures.contentSession(),
            fixtures.user(),
            ReservationStatus.CONFIRMED,
            null,
            null,
            null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newReservation(
            "R-20260802-002",
            "qr-reference-002",
            fixtures.region(),
            capacityHold,
            anotherContentSession,
            fixtures.user(),
            ReservationStatus.CONFIRMED,
            null,
            null,
            null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 예약은_상태에_맞는_취소와_만료_필드가_필요하다() {
        ReservationFixtures fixtures = createFixtures();

        assertThatThrownBy(() -> newReservation(
            "R-20260802-001",
            "qr-reference-001",
            fixtures.region(),
            newConsumedHold(fixtures),
            fixtures.contentSession(),
            fixtures.user(),
            ReservationStatus.CANCELLED,
            null,
            null,
            null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newReservation(
            "R-20260802-002",
            "qr-reference-002",
            fixtures.region(),
            newConsumedHold(fixtures),
            fixtures.contentSession(),
            fixtures.user(),
            ReservationStatus.EXPIRED,
            null,
            null,
            null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> newReservation(
            "R-20260802-003",
            "qr-reference-003",
            fixtures.region(),
            newConsumedHold(fixtures),
            fixtures.contentSession(),
            fixtures.user(),
            ReservationStatus.CONFIRMED,
            CANCELLED_AT,
            "개인 사정",
            null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 예약은_취소와_만료_상태별_필드를_저장한다() {
        ReservationFixtures fixtures = createFixtures();
        CapacityHold cancelledHold = saveConsumedHold(fixtures);
        CapacityHold expiredHold = saveConsumedHold(fixtures);

        Reservation cancelledReservation = reservationRepository.saveAndFlush(newReservation(
            "R-20260802-001",
            "qr-reference-001",
            fixtures.region(),
            cancelledHold,
            fixtures.contentSession(),
            null,
            ReservationStatus.CANCELLED,
            CANCELLED_AT,
            "개인 사정",
            null
        ));
        Reservation expiredReservation = reservationRepository.saveAndFlush(newReservation(
            "R-20260802-002",
            "qr-reference-002",
            fixtures.region(),
            expiredHold,
            fixtures.contentSession(),
            fixtures.user(),
            ReservationStatus.EXPIRED,
            null,
            null,
            EXPIRED_AT
        ));

        assertThat(cancelledReservation.getUser()).isNull();
        assertThat(cancelledReservation.getCancelledAt()).isEqualTo(CANCELLED_AT);
        assertThat(cancelledReservation.getCancellationReason()).isEqualTo("개인 사정");
        assertThat(expiredReservation.getExpiredAt()).isEqualTo(EXPIRED_AT);
        assertThat(expiredReservation.getCapacityReleasedAt()).isNull();
    }

    @Test
    void 예약은_QR_참조와_홀드를_각각_하나만_사용한다() {
        ReservationFixtures fixtures = createFixtures();
        CapacityHold firstHold = saveConsumedHold(fixtures);
        CapacityHold secondHold = saveConsumedHold(fixtures);

        Reservation firstReservation = reservationRepository.saveAndFlush(newReservation(
            "R-20260802-001",
            "qr-reference-001",
            fixtures.region(),
            firstHold,
            fixtures.contentSession(),
            fixtures.user(),
            ReservationStatus.CONFIRMED,
            null,
            null,
            null
        ));

        assertThatThrownBy(() -> insertReservation(
            "R-20260802-002",
            firstReservation.getQrReference(),
            secondHold,
            fixtures
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertReservation(
            "R-20260802-003",
            "qr-reference-003",
            firstHold,
            fixtures
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 예약번호는_지역과_회차와_홀드가_달라도_전역에서_유일하다() {
        ReservationFixtures firstFixtures = createFixtures();
        CapacityHold firstHold = saveConsumedHold(firstFixtures);
        reservationRepository.saveAndFlush(newReservation(
            "R-20260802-001",
            "qr-reference-001",
            firstFixtures.region(),
            firstHold,
            firstFixtures.contentSession(),
            firstFixtures.user(),
            ReservationStatus.CONFIRMED,
            null,
            null,
            null
        ));

        Region anotherRegion = saveRegion("BUSAN");
        ContentSession anotherContentSession = saveContentSession(anotherRegion);
        CapacityHold anotherHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            anotherRegion,
            anotherContentSession,
            firstFixtures.user(),
            1,
            CapacityHoldStatus.CONSUMED,
            CONFIRMED_AT,
            TERMINAL_AT,
            null,
            null
        ));

        assertThatThrownBy(() -> insertReservation(
            "R-20260802-001",
            "qr-reference-002",
            anotherHold,
            new ReservationFixtures(anotherRegion, anotherContentSession, firstFixtures.user())
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Reservation newReservation(
        String reservationNo,
        String qrReference,
        Region region,
        CapacityHold capacityHold,
        ContentSession contentSession,
        AppUser user,
        ReservationStatus status,
        Instant cancelledAt,
        String cancellationReason,
        Instant expiredAt
    ) {
        return new Reservation(
            reservationNo,
            qrReference,
            region,
            capacityHold,
            contentSession,
            user,
            status,
            CONFIRMED_AT,
            cancelledAt,
            cancellationReason,
            expiredAt,
            null
        );
    }

    private void insertReservation(
        String reservationNo,
        String qrReference,
        CapacityHold capacityHold,
        ReservationFixtures fixtures
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO reservation (
                    reservation_no,
                    qr_reference,
                    region_id,
                    hold_id,
                    session_id,
                    user_id,
                    status,
                    confirmed_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            reservationNo,
            qrReference,
            fixtures.region().getRegionId(),
            capacityHold.getHoldId(),
            fixtures.contentSession().getSessionId(),
            fixtures.user().getUserId(),
            ReservationStatus.CONFIRMED.name(),
            Timestamp.from(CONFIRMED_AT),
            Timestamp.from(CONFIRMED_AT)
        );
    }

    private CapacityHold saveConsumedHold(ReservationFixtures fixtures) {
        return capacityHoldRepository.saveAndFlush(newConsumedHold(fixtures));
    }

    private CapacityHold newConsumedHold(ReservationFixtures fixtures) {
        return new CapacityHold(
            fixtures.region(),
            fixtures.contentSession(),
            fixtures.user(),
            1,
            CapacityHoldStatus.CONSUMED,
            CONFIRMED_AT,
            TERMINAL_AT,
            null,
            null
        );
    }

    private ReservationFixtures createFixtures() {
        Region region = saveRegion("GIMHAE");
        ContentSession contentSession = saveContentSession(region);
        AppUser user = saveUser("visitor@example.com");
        return new ReservationFixtures(region, contentSession, user);
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
        AppUser reviewer = saveUser("reviewer-" + content.getContentId() + "@example.com");

        ContentSession contentSession = new ContentSession(
            content,
            region,
            Instant.parse("2026-08-02T01:00:00Z"),
            Instant.parse("2026-08-02T03:00:00Z"),
            Instant.parse("2026-08-02T00:30:00Z"),
            Instant.parse("2026-08-02T02:30:00Z"),
            20
        );
        contentSession.approve(reviewer, CONFIRMED_AT);
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

    private record ReservationFixtures(
        Region region,
        ContentSession contentSession,
        AppUser user
    ) {
    }
}
