package io.regionevent.regioneventbackend.domain.stampbook.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.hibernate.Hibernate;
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
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampEarn;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgress;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class StampEarnRepositoryTest {

    private static final Instant ISSUE_STARTS_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant ISSUE_ENDS_AT = Instant.parse("2026-08-31T23:59:59Z");
    private static final Instant CONFIRMED_AT = Instant.parse("2026-08-02T00:00:00Z");
    private static final Instant HOLD_TERMINAL_AT = Instant.parse("2026-08-01T23:59:00Z");
    private static final Instant CHECKED_AT = Instant.parse("2026-08-02T01:05:00Z");
    private static final Instant FIRST_EARNED_AT = Instant.parse("2026-08-03T00:00:00Z");
    private static final Instant LATEST_EARNED_AT = Instant.parse("2026-08-04T00:00:00Z");

    private final StampEarnRepository stampEarnRepository;
    private final StampbookProgressRepository stampbookProgressRepository;
    private final StampbookRepository stampbookRepository;
    private final CouponPolicyRepository couponPolicyRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final RegionRepository regionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final VisitRepository visitRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    StampEarnRepositoryTest(
        StampEarnRepository stampEarnRepository,
        StampbookProgressRepository stampbookProgressRepository,
        StampbookRepository stampbookRepository,
        CouponPolicyRepository couponPolicyRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        RegionRepository regionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        VisitRepository visitRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.stampEarnRepository = stampEarnRepository;
        this.stampbookProgressRepository = stampbookProgressRepository;
        this.stampbookRepository = stampbookRepository;
        this.couponPolicyRepository = couponPolicyRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.regionRepository = regionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.visitRepository = visitRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 스탬프_적립_근거는_진행_방문_콘텐츠를_지연_로딩으로_저장한다() {
        StampEarnFixtures fixtures = createFixtures();
        Visit visit = saveVisit(fixtures, fixtures.stampbookContent(), fixtures.user(), "first");
        StampEarn stampEarn = stampEarnRepository.saveAndFlush(new StampEarn(
            fixtures.progress(),
            visit,
            fixtures.stampbookContent(),
            FIRST_EARNED_AT
        ));
        entityManager.clear();

        StampEarn foundStampEarn = stampEarnRepository.findById(stampEarn.getStampEarnId()).orElseThrow();

        assertThat(foundStampEarn.getEarnedAt()).isEqualTo(FIRST_EARNED_AT);
        assertThat(Hibernate.isInitialized(foundStampEarn.getStampbookProgress())).isFalse();
        assertThat(Hibernate.isInitialized(foundStampEarn.getVisit())).isFalse();
        assertThat(Hibernate.isInitialized(foundStampEarn.getContent())).isFalse();
    }

    @Test
    void 진행별_적립수_콘텐츠별_적립여부와_최신순_이력을_조회한다() {
        StampEarnFixtures fixtures = createFixtures();
        Content secondContent = saveContent(fixtures.region(), "second-content");
        Content thirdContent = saveContent(fixtures.region(), "third-content");
        StampEarn first = saveStampEarn(
            fixtures,
            fixtures.stampbookContent(),
            FIRST_EARNED_AT,
            "first"
        );
        StampEarn second = saveStampEarn(fixtures, secondContent, LATEST_EARNED_AT, "second");
        StampEarn third = saveStampEarn(fixtures, thirdContent, LATEST_EARNED_AT, "third");
        entityManager.clear();

        long earnedCount = stampEarnRepository.countByStampbookProgressStampbookProgressId(
            fixtures.progress().getStampbookProgressId()
        );
        boolean isSecondContentEarned = stampEarnRepository
            .existsByStampbookProgressStampbookProgressIdAndContentContentId(
                fixtures.progress().getStampbookProgressId(),
                secondContent.getContentId()
            );
        boolean isUnknownContentEarned = stampEarnRepository
            .existsByStampbookProgressStampbookProgressIdAndContentContentId(
                fixtures.progress().getStampbookProgressId(),
                9_999L
            );
        List<StampEarn> earnings = stampEarnRepository
            .findByStampbookProgressStampbookProgressIdOrderByEarnedAtDescStampEarnIdDesc(
                fixtures.progress().getStampbookProgressId()
            );

        assertThat(earnedCount).isEqualTo(3);
        assertThat(isSecondContentEarned).isTrue();
        assertThat(isUnknownContentEarned).isFalse();
        assertThat(earnings)
            .extracting(StampEarn::getStampEarnId)
            .containsExactly(third.getStampEarnId(), second.getStampEarnId(), first.getStampEarnId());
        assertThat(Hibernate.isInitialized(earnings.getFirst().getVisit())).isTrue();
        assertThat(Hibernate.isInitialized(earnings.getFirst().getContent())).isTrue();
    }

    @Test
    void 같은_진행의_동일_콘텐츠_적립은_저장할_수_없다() {
        StampEarnFixtures fixtures = createFixtures();
        saveStampEarn(fixtures, fixtures.stampbookContent(), FIRST_EARNED_AT, "first");
        Visit anotherVisit = saveVisit(
            fixtures,
            fixtures.stampbookContent(),
            fixtures.user(),
            "another"
        );

        assertThatThrownBy(() -> stampEarnRepository.saveAndFlush(new StampEarn(
            fixtures.progress(),
            anotherVisit,
            fixtures.stampbookContent(),
            LATEST_EARNED_AT
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_진행의_동일_방문_적립은_저장할_수_없다() {
        StampEarnFixtures fixtures = createFixtures();
        Visit visit = saveVisit(fixtures, fixtures.stampbookContent(), fixtures.user(), "first");
        stampEarnRepository.saveAndFlush(new StampEarn(
            fixtures.progress(),
            visit,
            fixtures.stampbookContent(),
            FIRST_EARNED_AT
        ));
        Content anotherContent = saveContent(fixtures.region(), "another-content");

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
                INSERT INTO stamp_earn (
                    stampbook_progress_id,
                    visit_id,
                    content_id,
                    earned_at
                ) VALUES (?, ?, ?, ?)
                """,
            fixtures.progress().getStampbookProgressId(),
            visit.getVisitId(),
            anotherContent.getContentId(),
            Timestamp.from(LATEST_EARNED_AT)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 스탬프_적립_근거는_진행_사용자와_방문_사용자의_일치를_검증한다() {
        StampEarnFixtures fixtures = createFixtures();
        AppUser anotherUser = saveUser("another-visitor@example.com");
        Visit anotherUserVisit = saveVisit(
            fixtures,
            fixtures.stampbookContent(),
            anotherUser,
            "another-user"
        );

        assertThatThrownBy(() -> new StampEarn(
            fixtures.progress(),
            anotherUserVisit,
            fixtures.stampbookContent(),
            FIRST_EARNED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 스탬프_적립_근거는_방문_콘텐츠와_적립_콘텐츠의_일치를_검증한다() {
        StampEarnFixtures fixtures = createFixtures();
        Content anotherContent = saveContent(fixtures.region(), "another-content");
        Visit anotherContentVisit = saveVisit(fixtures, anotherContent, fixtures.user(), "another-content");

        assertThatThrownBy(() -> new StampEarn(
            fixtures.progress(),
            anotherContentVisit,
            fixtures.stampbookContent(),
            FIRST_EARNED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private StampEarn saveStampEarn(
        StampEarnFixtures fixtures,
        Content content,
        Instant earnedAt,
        String reference
    ) {
        Visit visit = saveVisit(fixtures, content, fixtures.user(), reference);
        return stampEarnRepository.saveAndFlush(new StampEarn(
            fixtures.progress(),
            visit,
            content,
            earnedAt
        ));
    }

    private StampEarnFixtures createFixtures() {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        AppUser user = saveUser("visitor@example.com");
        Content stampbookContent = saveContent(region, "stampbook-content");
        CouponPolicy rewardCouponPolicy = couponPolicyRepository.saveAndFlush(new CouponPolicy(
            stampbookContent,
            region,
            "스탬프북 완료 보상",
            "스탬프북 완료 시 발급하는 할인 쿠폰입니다.",
            CouponIssuanceType.STAMPBOOK_COMPLETION,
            3_000,
            10_000,
            30,
            ISSUE_STARTS_AT,
            ISSUE_ENDS_AT,
            100L
        ));
        Stampbook stampbook = stampbookRepository.saveAndFlush(new Stampbook(region, rewardCouponPolicy));
        StampbookProgress progress = stampbookProgressRepository.saveAndFlush(
            new StampbookProgress(stampbook, user)
        );
        return new StampEarnFixtures(region, user, stampbookContent, progress);
    }

    private Visit saveVisit(
        StampEarnFixtures fixtures,
        Content content,
        AppUser user,
        String reference
    ) {
        ContentSession contentSession = contentSessionRepository.saveAndFlush(new ContentSession(
            content,
            fixtures.region(),
            Instant.parse("2026-08-02T01:00:00Z"),
            Instant.parse("2026-08-02T03:00:00Z"),
            Instant.parse("2026-08-02T00:30:00Z"),
            Instant.parse("2026-08-02T02:30:00Z"),
            20
        ));
        AppUser checkinOperator = saveUser("checkin-operator-" + reference + "@example.com");
        CapacityHold capacityHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            fixtures.region(),
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
            "R-20260802-" + reference,
            "qr-reference-" + reference,
            fixtures.region(),
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
        return visitRepository.saveAndFlush(new Visit(
            fixtures.region(),
            reservation,
            user,
            content,
            contentSession,
            checkinOperator,
            CheckinMethod.QR,
            CHECKED_AT
        ));
    }

    private Content saveContent(
        Region region,
        String reference
    ) {
        AppUser operator = saveUser("operator-" + reference + "@example.com");
        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 가야 문화 체험 " + reference,
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            ISSUE_STARTS_AT
        ));
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private record StampEarnFixtures(
        Region region,
        AppUser user,
        Content stampbookContent,
        StampbookProgress progress
    ) {
    }
}
