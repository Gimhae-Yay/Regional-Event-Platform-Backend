package io.regionevent.regioneventbackend.domain.review.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

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
import io.regionevent.regioneventbackend.domain.review.repository.ReviewRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;

@SpringBootTest
@AutoConfigureMockMvc
class PublicContentReviewControllerIntegrationTest {

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final VisitRepository visitRepository;
    private final ReviewRepository reviewRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PublicContentReviewControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        VisitRepository visitRepository,
        ReviewRepository reviewRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.visitRepository = visitRepository;
        this.reviewRepository = reviewRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void getPublicContentReviews_returnsPublishedReviewsInFixedOrderWithAnonymousAuthors() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED);
        Review older = saveReview(fixture, "older", ReviewStatus.PUBLISHED, 4, "오래된 후기");
        Review sameTime = saveReview(fixture, "same-time", ReviewStatus.PUBLISHED, 5, "같은 시각 후기");
        Review latest = saveReview(fixture, "latest", ReviewStatus.PUBLISHED, 3, "최신 후기");
        latest.unlinkAuthor(Instant.parse("2026-08-04T00:00:00Z"));
        reviewRepository.saveAndFlush(latest);
        updateReviewTime(older, "2026-08-01T00:00:00Z");
        updateReviewTime(sameTime, "2026-08-02T00:00:00Z");
        updateReviewTime(latest, "2026-08-02T00:00:00Z");

        mockMvc.perform(get("/api/v1/contents/{contentId}/reviews", fixture.content().getContentId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("인증 후기 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.totalPages").value(1))
            .andExpect(jsonPath("$.data.content[0].reviewId").value(latest.getReviewId().toString()))
            .andExpect(jsonPath("$.data.content[0].authorDisplayName").value("탈퇴한 사용자"))
            .andExpect(jsonPath("$.data.content[1].reviewId").value(sameTime.getReviewId().toString()))
            .andExpect(jsonPath("$.data.content[1].authorDisplayName").value("인증 방문자"))
            .andExpect(jsonPath("$.data.content[2].reviewId").value(older.getReviewId().toString()))
            .andExpect(jsonPath("$.data.content[2].rating").value(4))
            .andExpect(jsonPath("$.data.content[2].reviewText").value("오래된 후기"))
            .andExpect(content().string(not(containsString("userId"))))
            .andExpect(content().string(not(containsString("visitId"))))
            .andExpect(content().string(not(containsString("regionId"))))
            .andExpect(content().string(not(containsString("latest@"))));
    }

    @Test
    void getPublicContentReviews_returnsEmptyResultForContentWithoutPublishedReviews() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED);

        mockMvc.perform(get("/api/v1/contents/{contentId}/reviews", fixture.content().getContentId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isEmpty())
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(0))
            .andExpect(jsonPath("$.data.totalPages").value(0));
    }

    @Test
    void getPublicContentReviews_returnsRequestedPageAndEmptyPage() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED);
        saveReview(fixture, "first", ReviewStatus.PUBLISHED, 5, "첫 번째 후기");
        saveReview(fixture, "second", ReviewStatus.PUBLISHED, 4, "두 번째 후기");

        mockMvc.perform(get("/api/v1/contents/{contentId}/reviews", fixture.content().getContentId())
                .queryParam("page", "1")
                .queryParam("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(1))
            .andExpect(jsonPath("$.data.totalElements").value(2))
            .andExpect(jsonPath("$.data.totalPages").value(2));

        mockMvc.perform(get("/api/v1/contents/{contentId}/reviews", fixture.content().getContentId())
                .queryParam("page", "2")
                .queryParam("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isEmpty())
            .andExpect(jsonPath("$.data.page").value(2))
            .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void getPublicContentReviews_excludesDeletedAndPurgedReviews() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED);
        saveReview(fixture, "published", ReviewStatus.PUBLISHED, 5, "공개 후기");
        saveReview(fixture, "deleted", ReviewStatus.DELETED, 4, "삭제 후기");
        saveReview(fixture, "purged", ReviewStatus.DELETED, null, null);

        mockMvc.perform(get("/api/v1/contents/{contentId}/reviews", fixture.content().getContentId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(content().string(not(containsString("삭제 후기"))));
    }

    @Test
    void getPublicContentReviews_hidesMissingAndNonPublicContent() throws Exception {
        Fixture nonPublicFixture = createFixture(ContentStatus.PENDING);

        mockMvc.perform(get("/api/v1/contents/{contentId}/reviews", 9_999_999))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/contents/{contentId}/reviews", nonPublicFixture.content().getContentId()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getPublicContentReviews_rejectsInvalidPathAndPaginationValues() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED);

        mockMvc.perform(get("/api/v1/contents/not-a-number/reviews"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        mockMvc.perform(get("/api/v1/contents/01/reviews"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/contents/{contentId}/reviews", fixture.content().getContentId())
                .queryParam("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/contents/{contentId}/reviews", fixture.content().getContentId())
                .queryParam("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/contents/{contentId}/reviews", fixture.content().getContentId())
                .queryParam("page", "one"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    private Fixture createFixture(ContentStatus contentStatus) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "테스트 지역", true));
        AppUser operator = saveUser("operator-" + suffix + "@example.com");
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            contentStatus,
            "지역 체험",
            "지역 체험 설명",
            "경상남도 김해시",
            "10:00~18:00",
            "055-1234-5678",
            "안전 수칙을 지켜주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 전까지 취소할 수 있습니다.",
            Instant.now().minusSeconds(60)
        ));
        ContentSession session = new ContentSession(
            content,
            region,
            Instant.now().plusSeconds(3_600),
            Instant.now().plusSeconds(10_800),
            Instant.now().plusSeconds(1_800),
            Instant.now().plusSeconds(9_000),
            20
        );
        session.approve(operator, Instant.now());
        return new Fixture(
            region,
            operator,
            content,
            contentSessionRepository.saveAndFlush(session),
            suffix
        );
    }

    private Review saveReview(
        Fixture fixture,
        String suffix,
        ReviewStatus status,
        Integer rating,
        String reviewText
    ) {
        AppUser user = saveUser(suffix + "-" + fixture.suffix() + "@example.com");
        Instant now = Instant.now();
        CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            fixture.region(),
            fixture.session(),
            user,
            1,
            CapacityHoldStatus.CONSUMED,
            now.minusSeconds(600),
            now.minusSeconds(300),
            null,
            null
        ));
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "R-" + suffix + "-" + fixture.suffix(),
            UUID.randomUUID().toString(),
            fixture.region(),
            hold,
            fixture.session(),
            user,
            ReservationStatus.CONFIRMED,
            now.minusSeconds(300),
            null,
            null,
            null,
            null
        ));
        Visit visit = visitRepository.saveAndFlush(new Visit(
            fixture.region(),
            reservation,
            user,
            fixture.content(),
            fixture.session(),
            fixture.operator(),
            CheckinMethod.QR,
            now
        ));
        return reviewRepository.saveAndFlush(new Review(
            fixture.region(),
            visit,
            user,
            fixture.content(),
            rating,
            reviewText,
            status,
            status == ReviewStatus.DELETED ? now : null
        ));
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "방문자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private void updateReviewTime(Review review, String createdAt) {
        Instant timestamp = Instant.parse(createdAt);
        jdbcTemplate.update(
            "UPDATE review SET created_at = ?, updated_at = ? WHERE review_id = ?",
            Timestamp.from(timestamp),
            Timestamp.from(timestamp),
            review.getReviewId()
        );
    }

    private record Fixture(
        Region region,
        AppUser operator,
        Content content,
        ContentSession session,
        String suffix
    ) {
    }
}
