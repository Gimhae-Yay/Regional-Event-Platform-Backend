package io.regionevent.regioneventbackend.domain.review.repository;

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
import io.regionevent.regioneventbackend.domain.review.entity.Review;
import io.regionevent.regioneventbackend.domain.review.entity.ReviewStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class ReviewRepositoryTest {

    private static final Instant CONFIRMED_AT = Instant.parse("2026-08-02T00:00:00Z");
    private static final Instant HOLD_TERMINAL_AT = Instant.parse("2026-08-01T23:59:00Z");
    private static final Instant CHECKED_AT = Instant.parse("2026-08-02T01:05:00Z");
    private static final Instant DELETED_AT = Instant.parse("2026-08-03T00:00:00Z");
    private static final Instant AUTHOR_UNLINKED_AT = Instant.parse("2026-08-04T00:00:00Z");

    private final ReviewRepository reviewRepository;
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
    ReviewRepositoryTest(
        ReviewRepository reviewRepository,
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
        this.reviewRepository = reviewRepository;
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
    void 후기는_방문과_지역_작성자_콘텐츠를_지연_로딩으로_연결한다() {
        ReviewFixtures fixtures = createFixtures();
        Review review = reviewRepository.saveAndFlush(newPublishedReview(fixtures));
        entityManager.clear();

        Review foundReview = reviewRepository.findById(review.getReviewId()).orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(foundReview.getRating()).isEqualTo(5);
        assertThat(foundReview.getReviewText()).isEqualTo("김해 가야 문화를 즐겁게 체험했습니다.");
        assertThat(foundReview.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(foundReview.getCreatedAt()).isNotNull();
        assertThat(foundReview.getUpdatedAt()).isNotNull();
        assertThat(persistenceUnitUtil.isLoaded(foundReview, "visit")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundReview, "region")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundReview, "user")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundReview, "content")).isFalse();
        assertThat(foundReview.getVisit().getVisitId()).isEqualTo(fixtures.visit().getVisitId());
        assertThat(foundReview.getContent().getContentId()).isEqualTo(fixtures.content().getContentId());
    }

    @Test
    void 후기는_작성자_연결을_해제한_시각을_기록한다() {
        ReviewFixtures fixtures = createFixtures();
        Review review = reviewRepository.saveAndFlush(newPublishedReview(fixtures));

        review.unlinkAuthor(AUTHOR_UNLINKED_AT);
        reviewRepository.flush();
        entityManager.clear();

        Review foundReview = reviewRepository.findById(review.getReviewId()).orElseThrow();

        assertThat(foundReview.getUser()).isNull();
        assertThat(foundReview.getAuthorUnlinkedAt()).isEqualTo(AUTHOR_UNLINKED_AT);
    }

    @Test
    void 후기는_상태별_원문_필드_조건을_검증한다() {
        ReviewFixtures fixtures = createFixtures();

        assertThatThrownBy(() -> new Review(
            fixtures.region(),
            fixtures.visit(),
            fixtures.user(),
            fixtures.content(),
            null,
            null,
            ReviewStatus.PUBLISHED,
            null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Review(
            fixtures.region(),
            fixtures.visit(),
            fixtures.user(),
            fixtures.content(),
            5,
            "원문이 하나만 남으면 안 됩니다.",
            ReviewStatus.DELETED,
            null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Review(
            fixtures.region(),
            fixtures.visit(),
            fixtures.user(),
            fixtures.content(),
            5,
            null,
            ReviewStatus.DELETED,
            DELETED_AT
        )).isInstanceOf(IllegalArgumentException.class);

        Review purgedReview = reviewRepository.saveAndFlush(new Review(
            fixtures.region(),
            fixtures.visit(),
            fixtures.user(),
            fixtures.content(),
            null,
            null,
            ReviewStatus.DELETED,
            DELETED_AT
        ));

        assertThat(purgedReview.getDeletedAt()).isEqualTo(DELETED_AT);
        assertThat(purgedReview.getRating()).isNull();
        assertThat(purgedReview.getReviewText()).isNull();
    }

    @Test
    void 후기는_방문당_한_건만_저장한다() {
        ReviewFixtures fixtures = createFixtures();
        reviewRepository.saveAndFlush(newPublishedReview(fixtures));

        assertThatThrownBy(() -> insertReview(
            fixtures.region(),
            fixtures.visit(),
            fixtures.user(),
            fixtures.content(),
            4,
            "두 번째 후기는 저장할 수 없습니다.",
            ReviewStatus.PUBLISHED.name(),
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 후기는_방문의_콘텐츠와_지역이_일치해야_한다() {
        ReviewFixtures fixtures = createFixtures();
        Region anotherRegion = saveRegion("BUSAN");
        Content anotherContent = saveContent(anotherRegion, "busan-operator@example.com");

        assertThatThrownBy(() -> new Review(
            anotherRegion,
            fixtures.visit(),
            fixtures.user(),
            anotherContent,
            5,
            "다른 지역 콘텐츠에는 연결할 수 없습니다.",
            ReviewStatus.PUBLISHED,
            null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> insertReview(
            fixtures.region(),
            fixtures.visit(),
            fixtures.user(),
            anotherContent,
            5,
            "방문 콘텐츠와 다른 콘텐츠에는 연결할 수 없습니다.",
            ReviewStatus.PUBLISHED.name(),
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 후기는_데이터베이스에서도_상태별_원문_필드_조건을_강제한다() {
        ReviewFixtures fixtures = createFixtures();

        assertThatThrownBy(() -> insertReview(
            fixtures.region(),
            fixtures.visit(),
            fixtures.user(),
            fixtures.content(),
            null,
            null,
            ReviewStatus.PUBLISHED.name(),
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertReview(
            fixtures.region(),
            fixtures.visit(),
            fixtures.user(),
            fixtures.content(),
            5,
            null,
            ReviewStatus.DELETED.name(),
            DELETED_AT
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Review newPublishedReview(ReviewFixtures fixtures) {
        return new Review(
            fixtures.region(),
            fixtures.visit(),
            fixtures.user(),
            fixtures.content(),
            5,
            "김해 가야 문화를 즐겁게 체험했습니다.",
            ReviewStatus.PUBLISHED,
            null
        );
    }

    private void insertReview(
        Region region,
        Visit visit,
        AppUser user,
        Content content,
        Integer rating,
        String reviewText,
        String status,
        Instant deletedAt
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO review (
                    region_id,
                    visit_id,
                    user_id,
                    content_id,
                    rating,
                    review_text,
                    status,
                    created_at,
                    updated_at,
                    deleted_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            region.getRegionId(),
            visit.getVisitId(),
            user.getUserId(),
            content.getContentId(),
            rating,
            reviewText,
            status,
            Timestamp.from(CHECKED_AT),
            Timestamp.from(CHECKED_AT),
            deletedAt == null ? null : Timestamp.from(deletedAt)
        );
    }

    private ReviewFixtures createFixtures() {
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
        Visit visit = visitRepository.saveAndFlush(new Visit(
            region,
            reservation,
            user,
            content,
            contentSession,
            checkinOperator,
            CheckinMethod.QR,
            CHECKED_AT
        ));
        return new ReviewFixtures(region, content, user, visit);
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
            "055-1234-5678",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            Instant.parse("2026-08-01T00:00:00Z")
        ));
    }

    private ContentSession saveContentSession(Content content, Region region) {
        return contentSessionRepository.saveAndFlush(new ContentSession(
            content,
            region,
            Instant.parse("2026-08-02T01:00:00Z"),
            Instant.parse("2026-08-02T03:00:00Z"),
            Instant.parse("2026-08-02T00:30:00Z"),
            Instant.parse("2026-08-02T02:30:00Z"),
            20
        ));
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

    private record ReviewFixtures(
        Region region,
        Content content,
        AppUser user,
        Visit visit
    ) {
    }
}
