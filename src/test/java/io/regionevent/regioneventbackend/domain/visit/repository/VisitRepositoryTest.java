package io.regionevent.regioneventbackend.domain.visit.repository;

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
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class VisitRepositoryTest {

    private static final Instant CONFIRMED_AT = Instant.parse("2026-08-02T00:00:00Z");
    private static final Instant HOLD_TERMINAL_AT = Instant.parse("2026-08-01T23:59:00Z");
    private static final Instant CHECKED_AT = Instant.parse("2026-08-02T01:05:00Z");
    private static final Instant AUTHOR_UNLINKED_AT = Instant.parse("2026-08-03T00:00:00Z");

    private final VisitRepository visitRepository;
    private final ReservationRepository reservationRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final RegionRepository regionRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    VisitRepositoryTest(
        VisitRepository visitRepository,
        ReservationRepository reservationRepository,
        CapacityHoldRepository capacityHoldRepository,
        RegionRepository regionRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.visitRepository = visitRepository;
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
    void 방문은_예약과_콘텐츠_회차_지역_체크인_처리자를_지연_로딩으로_연결한다() {
        VisitFixtures fixtures = createFixtures();
        Visit visit = visitRepository.saveAndFlush(newVisit(fixtures, CheckinMethod.QR));
        entityManager.clear();

        Visit foundVisit = visitRepository.findById(visit.getVisitId()).orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(foundVisit.getCheckinMethod()).isEqualTo(CheckinMethod.QR);
        assertThat(foundVisit.getCheckedAt()).isEqualTo(CHECKED_AT);
        assertThat(persistenceUnitUtil.isLoaded(foundVisit, "reservation")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundVisit, "region")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundVisit, "content")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundVisit, "contentSession")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundVisit, "user")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundVisit, "checkedInByUser")).isFalse();
        assertThat(foundVisit.getReservation().getReservationId())
            .isEqualTo(fixtures.reservation().getReservationId());
        assertThat(foundVisit.getContentSession().getSessionId())
            .isEqualTo(fixtures.contentSession().getSessionId());
        assertThat(foundVisit.getCheckedInByUser().getUserId())
            .isEqualTo(fixtures.checkinOperator().getUserId());
    }

    @Test
    void 방문은_작성자_연결을_해제한_시각을_기록한다() {
        VisitFixtures fixtures = createFixtures();
        Visit visit = visitRepository.saveAndFlush(newVisit(fixtures, CheckinMethod.RESERVATION_NUMBER));

        visit.unlinkAuthor(AUTHOR_UNLINKED_AT);
        visitRepository.flush();
        entityManager.clear();

        Visit foundVisit = visitRepository.findById(visit.getVisitId()).orElseThrow();

        assertThat(foundVisit.getUser()).isNull();
        assertThat(foundVisit.getAuthorUnlinkedAt()).isEqualTo(AUTHOR_UNLINKED_AT);
    }

    @Test
    void 방문은_예약_참여자와_다른_사용자로_생성할_수_없다() {
        VisitFixtures fixtures = createFixtures();
        AppUser anotherUser = saveUser("another-visitor@example.com");

        assertThatThrownBy(() -> new Visit(
            fixtures.region(),
            fixtures.reservation(),
            anotherUser,
            fixtures.content(),
            fixtures.contentSession(),
            fixtures.checkinOperator(),
            CheckinMethod.QR,
            CHECKED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 방문은_작성자_없이_생성할_수_없다() {
        VisitFixtures fixtures = createFixtures();

        assertThatThrownBy(() -> new Visit(
            fixtures.region(),
            fixtures.reservation(),
            null,
            fixtures.content(),
            fixtures.contentSession(),
            fixtures.checkinOperator(),
            CheckinMethod.QR,
            CHECKED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 작성자_연결_해제_시각이_없으면_방문_작성자_연결을_유지한다() {
        VisitFixtures fixtures = createFixtures();
        Visit visit = newVisit(fixtures, CheckinMethod.QR);

        assertThatThrownBy(() -> visit.unlinkAuthor(null))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(visit.getUser()).isSameAs(fixtures.user());
        assertThat(visit.getAuthorUnlinkedAt()).isNull();
    }

    @Test
    void 방문은_예약당_한_건만_저장한다() {
        VisitFixtures fixtures = createFixtures();
        visitRepository.saveAndFlush(newVisit(fixtures, CheckinMethod.QR));

        assertThatThrownBy(() -> insertVisit(
            fixtures.reservation(),
            fixtures.contentSession(),
            fixtures.content(),
            fixtures.region(),
            fixtures.user(),
            fixtures.checkinOperator(),
            CheckinMethod.QR.name()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 방문은_예약과_회차와_콘텐츠와_지역의_복합_관계를_강제한다() {
        VisitFixtures fixtures = createFixtures();
        ContentSession anotherContentSession = saveContentSession(fixtures.content(), fixtures.region());

        assertThatThrownBy(() -> insertVisit(
            fixtures.reservation(),
            anotherContentSession,
            fixtures.content(),
            fixtures.region(),
            fixtures.user(),
            fixtures.checkinOperator(),
            CheckinMethod.QR.name()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 방문은_지원하는_체크인_방법만_저장한다() {
        VisitFixtures fixtures = createFixtures();

        assertThatThrownBy(() -> insertVisit(
            fixtures.reservation(),
            fixtures.contentSession(),
            fixtures.content(),
            fixtures.region(),
            fixtures.user(),
            fixtures.checkinOperator(),
            "MANUAL"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 방문은_예약과_콘텐츠_회차_지역이_일치해야_한다() {
        VisitFixtures fixtures = createFixtures();
        Region anotherRegion = saveRegion("BUSAN");
        Content anotherContent = saveContent(anotherRegion, "busan-operator@example.com");
        ContentSession anotherContentSession = saveContentSession(anotherContent, anotherRegion);

        assertThatThrownBy(() -> new Visit(
            anotherRegion,
            fixtures.reservation(),
            fixtures.user(),
            anotherContent,
            anotherContentSession,
            fixtures.checkinOperator(),
            CheckinMethod.QR,
            CHECKED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private Visit newVisit(VisitFixtures fixtures, CheckinMethod checkinMethod) {
        return new Visit(
            fixtures.region(),
            fixtures.reservation(),
            fixtures.user(),
            fixtures.content(),
            fixtures.contentSession(),
            fixtures.checkinOperator(),
            checkinMethod,
            CHECKED_AT
        );
    }

    private void insertVisit(
        Reservation reservation,
        ContentSession contentSession,
        Content content,
        Region region,
        AppUser user,
        AppUser checkedInByUser,
        String checkinMethod
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO visit (
                    region_id,
                    reservation_id,
                    user_id,
                    content_id,
                    session_id,
                    checked_in_by_user_id,
                    checkin_method,
                    checked_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
            region.getRegionId(),
            reservation.getReservationId(),
            user.getUserId(),
            content.getContentId(),
            contentSession.getSessionId(),
            checkedInByUser.getUserId(),
            checkinMethod,
            Timestamp.from(CHECKED_AT)
        );
    }

    private VisitFixtures createFixtures() {
        Region region = saveRegion("GIMHAE");
        Content content = saveContent(region, "operator@example.com");
        ContentSession contentSession = saveContentSession(content, region);
        AppUser user = saveUser("visitor@example.com");
        AppUser checkinOperator = saveUser("checkin-operator@example.com");
        CapacityHold capacityHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            contentSession,
            user,
            1,
            CapacityHoldStatus.CONSUMED,
            CONFIRMED_AT,
            HOLD_TERMINAL_AT,
            null,
            null
        ));
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "R-20260802-001",
            "qr-reference-001",
            region,
            capacityHold,
            contentSession,
            user,
            ReservationStatus.CONFIRMED,
            CONFIRMED_AT,
            null,
            null,
            null,
            null
        ));
        return new VisitFixtures(region, content, contentSession, user, checkinOperator, reservation);
    }

    private Region saveRegion(String regionCode) {
        return regionRepository.saveAndFlush(new Region(regionCode, regionCode + "시", true));
    }

    private Content saveContent(Region region, String operatorLoginIdentifier) {
        AppUser operator = saveUser(operatorLoginIdentifier);
        return contentRepository.saveAndFlush(new Content(
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
    }

    private ContentSession saveContentSession(Content content, Region region) {
        ContentSession contentSession = new ContentSession(
            content,
            region,
            Instant.parse("2026-08-02T01:00:00Z"),
            Instant.parse("2026-08-02T03:00:00Z"),
            Instant.parse("2026-08-02T00:30:00Z"),
            Instant.parse("2026-08-02T02:30:00Z"),
            20
        );
        contentSession.approve(content.getOperator(), CONFIRMED_AT);
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

    private record VisitFixtures(
        Region region,
        Content content,
        ContentSession contentSession,
        AppUser user,
        AppUser checkinOperator,
        Reservation reservation
    ) {
    }
}
