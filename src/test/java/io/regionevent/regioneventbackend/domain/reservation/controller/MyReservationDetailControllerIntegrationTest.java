package io.regionevent.regioneventbackend.domain.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class MyReservationDetailControllerIntegrationTest {

    private static final Instant SESSION_STARTS_AT = Instant.parse("2030-08-10T01:00:00Z");
    private static final Instant SESSION_ENDS_AT = Instant.parse("2030-08-10T03:00:00Z");
    private static final Instant CHECKIN_OPEN_AT = Instant.parse("2030-08-10T00:30:00Z");
    private static final Instant CHECKIN_CLOSE_AT = Instant.parse("2030-08-10T01:30:00Z");
    private static final Instant CONFIRMED_AT = Instant.parse("2030-08-01T01:00:00Z");
    private static final Instant CANCELLED_AT = Instant.parse("2030-08-02T01:00:00Z");
    private static final Instant EXPIRED_AT = Instant.parse("2030-08-03T01:00:00Z");
    private static final Instant CHECKED_AT = Instant.parse("2030-08-04T01:00:00Z");

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final VisitRepository visitRepository;
    private final ReviewRepository reviewRepository;
    private final AuditEventRepository auditEventRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;

    @Autowired
    MyReservationDetailControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        VisitRepository visitRepository,
        ReviewRepository reviewRepository,
        AuditEventRepository auditEventRepository,
        JwtAccessTokenService jwtAccessTokenService,
        EntityManager entityManager
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
        this.auditEventRepository = auditEventRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.entityManager = entityManager;
    }

    @Test
    void 예약_상세_조회_모든_예약_상태의_계약_필드와_체크인_정보를_반환하고_상태를_변경하지_않는다(
        CapturedOutput output
    ) throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE);
        Reservation confirmed = saveReservation(fixture, ReservationStatus.CONFIRMED, "confirmed");
        Reservation checkedIn = saveReservation(fixture, ReservationStatus.CHECKED_IN, "checked-in");
        Reservation cancelled = saveReservation(fixture, ReservationStatus.CANCELLED, "cancelled");
        Reservation expired = saveReservation(fixture, ReservationStatus.EXPIRED, "expired");
        Visit visit = visitRepository.saveAndFlush(new Visit(
            fixture.region(),
            checkedIn,
            fixture.user(),
            fixture.content(),
            fixture.session(),
            fixture.operator(),
            CheckinMethod.QR,
            CHECKED_AT
        ));
        entityManager.flush();
        entityManager.clear();

        ResultActions confirmedResult = performGet(fixture.user(), confirmed.getReservationId());
        confirmedResult.andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("예약 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.reservation.reservationId").value(confirmed.getReservationId().toString()))
            .andExpect(jsonPath("$.data.reservation.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.data.reservation.quantity").value(1))
            .andExpect(jsonPath("$.data.reservation.confirmedAt").value("2030-08-01T01:00:00Z"))
            .andExpect(jsonPath("$.data.reservation.cancelledAt").doesNotExist())
            .andExpect(jsonPath("$.data.reservation.cancellationReason").doesNotExist())
            .andExpect(jsonPath("$.data.reservation.expiredAt").doesNotExist())
            .andExpect(jsonPath("$.data.session.sessionId").value(fixture.session().getSessionId().toString()))
            .andExpect(jsonPath("$.data.session.contentId").value(fixture.content().getContentId().toString()))
            .andExpect(jsonPath("$.data.session.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.data.session.startsAt").value("2030-08-10T10:00:00+09:00"))
            .andExpect(jsonPath("$.data.session.endsAt").value("2030-08-10T12:00:00+09:00"))
            .andExpect(jsonPath("$.data.session.checkinOpenAt").value("2030-08-10T09:30:00+09:00"))
            .andExpect(jsonPath("$.data.session.checkinCloseAt").value("2030-08-10T10:30:00+09:00"))
            .andExpect(jsonPath("$.data.content.contentId").value(fixture.content().getContentId().toString()))
            .andExpect(jsonPath("$.data.content.title").value("김해 문화 체험"))
            .andExpect(jsonPath("$.data.content.locationText").value("김해시"))
            .andExpect(jsonPath("$.data.checkIn.checkedIn").value(false))
            .andExpect(jsonPath("$.data.checkIn.checkedAt").doesNotExist())
            .andExpect(jsonPath("$.data.checkIn.visitId").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.review").value(org.hamcrest.Matchers.nullValue()));

        performGet(fixture.user(), checkedIn.getReservationId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reservation.status").value("CHECKED_IN"))
            .andExpect(jsonPath("$.data.reservation.quantity").value(1))
            .andExpect(jsonPath("$.data.checkIn.checkedIn").value(true))
            .andExpect(jsonPath("$.data.checkIn.checkedAt").value("2030-08-04T01:00:00Z"))
            .andExpect(jsonPath("$.data.checkIn.visitId").value(visit.getVisitId().toString()))
            .andExpect(jsonPath("$.data.review").value(org.hamcrest.Matchers.nullValue()));
        performGet(fixture.user(), cancelled.getReservationId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reservation.status").value("CANCELLED"))
            .andExpect(jsonPath("$.data.reservation.quantity").value(1))
            .andExpect(jsonPath("$.data.reservation.cancelledAt").value("2030-08-02T01:00:00Z"))
            .andExpect(jsonPath("$.data.reservation.cancellationReason").value("방문자 요청"))
            .andExpect(jsonPath("$.data.checkIn.checkedIn").value(false))
            .andExpect(jsonPath("$.data.checkIn.visitId").value(org.hamcrest.Matchers.nullValue()));
        performGet(fixture.user(), expired.getReservationId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reservation.status").value("EXPIRED"))
            .andExpect(jsonPath("$.data.reservation.quantity").value(1))
            .andExpect(jsonPath("$.data.reservation.expiredAt").value("2030-08-03T01:00:00Z"))
            .andExpect(jsonPath("$.data.checkIn.checkedIn").value(false))
            .andExpect(jsonPath("$.data.checkIn.visitId").value(org.hamcrest.Matchers.nullValue()));

        String responseBody = confirmedResult.andReturn().getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain(
            fixture.user().getName(),
            fixture.user().getPhone(),
            confirmed.getQrReference(),
            "qrReference",
            "userId"
        );
        assertThat(output.getOut()).contains(
            "Reservation detail read. requestId=",
            "reservationId=" + checkedIn.getReservationId()
                + ", sessionId=" + fixture.session().getSessionId()
                + ", visitId=" + visit.getVisitId()
                + ", resultCode=SUCCESS"
        ).doesNotContain(
            fixture.user().getName(),
            fixture.user().getPhone(),
            checkedIn.getQrReference()
        );
        assertReadDoesNotChangeState(
            fixture,
            List.of(confirmed, checkedIn, cancelled, expired),
            List.of(visit)
        );
    }

    @Test
    void 예약_상세_조회는_방문_후기의_작성과_삭제_원문_보존_파기_상태를_직렬화하고_변경하지_않는다()
        throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE);
        Reservation withPublishedReview = saveReservation(
            fixture,
            ReservationStatus.CHECKED_IN,
            "published-review"
        );
        Reservation withRetainedDeletedReview = saveReservation(
            fixture,
            ReservationStatus.CHECKED_IN,
            "retained-deleted-review"
        );
        Reservation withPurgedDeletedReview = saveReservation(
            fixture,
            ReservationStatus.CHECKED_IN,
            "purged-deleted-review"
        );
        Visit publishedVisit = saveVisit(fixture, withPublishedReview);
        Visit retainedDeletedVisit = saveVisit(fixture, withRetainedDeletedReview);
        Visit purgedDeletedVisit = saveVisit(fixture, withPurgedDeletedReview);
        Review publishedReview = reviewRepository.saveAndFlush(new Review(
            fixture.region(),
            publishedVisit,
            fixture.user(),
            fixture.content(),
            5,
            "공개 후기입니다.",
            ReviewStatus.PUBLISHED,
            null
        ));
        Review retainedDeletedReview = reviewRepository.saveAndFlush(new Review(
            fixture.region(),
            retainedDeletedVisit,
            fixture.user(),
            fixture.content(),
            4,
            "삭제 원문을 보존 중입니다.",
            ReviewStatus.DELETED,
            Instant.parse("2030-08-03T00:00:00Z")
        ));
        Review purgedDeletedReview = reviewRepository.saveAndFlush(new Review(
            fixture.region(),
            purgedDeletedVisit,
            fixture.user(),
            fixture.content(),
            null,
            null,
            ReviewStatus.DELETED,
            Instant.parse("2030-08-03T00:00:00Z")
        ));
        entityManager.flush();
        entityManager.clear();
        ReviewState publishedReviewState = ReviewState.from(
            reviewRepository.findById(publishedReview.getReviewId()).orElseThrow()
        );
        ReviewState retainedDeletedReviewState = ReviewState.from(reviewRepository.findById(
            retainedDeletedReview.getReviewId()
        ).orElseThrow());
        ReviewState purgedDeletedReviewState = ReviewState.from(reviewRepository.findById(
            purgedDeletedReview.getReviewId()
        ).orElseThrow());
        entityManager.clear();

        performGet(fixture.user(), withPublishedReview.getReservationId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.review.reviewId").value(publishedReview.getReviewId().toString()))
            .andExpect(jsonPath("$.data.review.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.review.rating").value(5))
            .andExpect(jsonPath("$.data.review.reviewText").value("공개 후기입니다."))
            .andExpect(jsonPath("$.data.review.createdAt").value(publishedReviewState.createdAt().toString()))
            .andExpect(jsonPath("$.data.review.updatedAt").value(publishedReviewState.updatedAt().toString()));
        performGet(fixture.user(), withRetainedDeletedReview.getReservationId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.review.reviewId").value(retainedDeletedReview.getReviewId().toString()))
            .andExpect(jsonPath("$.data.review.status").value("DELETED"))
            .andExpect(jsonPath("$.data.review.rating").value(4))
            .andExpect(jsonPath("$.data.review.reviewText").value("삭제 원문을 보존 중입니다."))
            .andExpect(jsonPath("$.data.review.createdAt").value(
                retainedDeletedReviewState.createdAt().toString()
            ))
            .andExpect(jsonPath("$.data.review.updatedAt").value(
                retainedDeletedReviewState.updatedAt().toString()
            ));
        performGet(fixture.user(), withPurgedDeletedReview.getReservationId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.review.reviewId").value(purgedDeletedReview.getReviewId().toString()))
            .andExpect(jsonPath("$.data.review.status").value("DELETED"))
            .andExpect(jsonPath("$.data.review.rating").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.review.reviewText").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.review.createdAt").value(purgedDeletedReviewState.createdAt().toString()))
            .andExpect(jsonPath("$.data.review.updatedAt").value(purgedDeletedReviewState.updatedAt().toString()));

        assertReadDoesNotChangeState(
            fixture,
            List.of(withPublishedReview, withRetainedDeletedReview, withPurgedDeletedReview),
            List.of(publishedVisit, retainedDeletedVisit, purgedDeletedVisit)
        );
        assertReviewsUnchanged(List.of(
            publishedReviewState,
            retainedDeletedReviewState,
            purgedDeletedReviewState
        ));
    }

    @Test
    void 예약_상세_조회_활성_회원이_아니거나_다른_회원의_예약이면_FORBIDDEN과_무변경을_반환한다()
        throws Exception {
        Fixture withdrawingFixture = createFixture(AppUserStatus.WITHDRAWING);
        Reservation withdrawingReservation = saveReservation(
            withdrawingFixture,
            ReservationStatus.CONFIRMED,
            "withdrawing"
        );
        Fixture activeFixture = createFixture(AppUserStatus.ACTIVE);
        Reservation activeReservation = saveReservation(activeFixture, ReservationStatus.CONFIRMED, "owned");
        AppUser anotherUser = saveUser(AppUserStatus.ACTIVE, "다른 방문자", "010-2000-0001");

        performGet(withdrawingFixture.user(), withdrawingReservation.getReservationId())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        performGet(anotherUser, activeReservation.getReservationId())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertReadDoesNotChangeState(withdrawingFixture, List.of(withdrawingReservation), List.of());
        assertReadDoesNotChangeState(activeFixture, List.of(activeReservation), List.of());
    }

    @Test
    void 예약_상세_조회_입력_대상_부재_미인증의_공통_오류_계약과_비식별_로그를_반환한다(
        CapturedOutput output
    ) throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE);
        String sensitiveReservationId = "someone@example.com";

        performGet(fixture.user(), 0L)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/me/reservations/{reservationId}", sensitiveReservationId)
                .header("Authorization", bearerToken(fixture.user())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        performGet(fixture.user(), 999_999L)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/me/reservations/{reservationId}", sensitiveReservationId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(output.getOut()).contains(
            "Reservation detail read. requestId=",
            "reservationId=null, sessionId=null, visitId=null, resultCode=INVALID_TYPE",
            "reservationId=null, sessionId=null, visitId=null, resultCode=UNAUTHENTICATED",
            "HTTP request completed. method=GET, uri=/api/v1/me/reservations/{reservationId}, status=401"
        ).doesNotContain(sensitiveReservationId);
        assertThat(auditEventRepository.findAll()).isEmpty();
    }

    @Test
    void 예약_상세_조회_체크인_방문_연결이_없으면_INTERNAL_SERVER_ERROR를_로그에_남기고_상태를_변경하지_않는다(
        CapturedOutput output
    ) throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE);
        Reservation reservation = saveReservation(fixture, ReservationStatus.CHECKED_IN, "inconsistent");
        entityManager.flush();
        entityManager.clear();

        performGet(fixture.user(), reservation.getReservationId())
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        assertThat(output.getOut()).contains(
            "Reservation detail read. requestId=",
            "reservationId=" + reservation.getReservationId()
                + ", sessionId=null, visitId=null, resultCode=INTERNAL_SERVER_ERROR"
        );
        assertReadDoesNotChangeState(fixture, List.of(reservation), List.of());
    }

    @Test
    void 예약_상세_조회는_콘텐츠_종료_중단_철회_뒤에도_콘텐츠_표시_정보를_반환한다() throws Exception {
        List<Consumer<Content>> lifecycleChanges = List.of(
            Content::end,
            Content::suspend,
            Content::withdraw
        );

        for (Consumer<Content> lifecycleChange : lifecycleChanges) {
            Fixture fixture = createFixture(AppUserStatus.ACTIVE);
            Reservation reservation = saveReservation(fixture, ReservationStatus.CONFIRMED, "content-lifecycle");
            lifecycleChange.accept(fixture.content());
            entityManager.flush();
            entityManager.clear();

            performGet(fixture.user(), reservation.getReservationId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.contentId").value(
                    fixture.content().getContentId().toString()
                ))
                .andExpect(jsonPath("$.data.content.contentId").value(
                    fixture.content().getContentId().toString()
                ))
                .andExpect(jsonPath("$.data.content.title").value("김해 문화 체험"))
                .andExpect(jsonPath("$.data.content.locationText").value("김해시"));
        }
    }

    private ResultActions performGet(AppUser user, Long reservationId) throws Exception {
        return mockMvc.perform(get("/api/v1/me/reservations/{reservationId}", reservationId)
            .header("Authorization", bearerToken(user)));
    }

    private Fixture createFixture(AppUserStatus userStatus) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("G" + suffix, "김해시", true));
        AppUser operator = saveUser(AppUserStatus.ACTIVE, "운영자", "010-3000-0001");
        AppUser user = saveUser(userStatus, "김민수", "010-1000-0001");
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 문화 체험",
            "콘텐츠 설명",
            "김해시",
            "10:00~18:00",
            "055-000-0000",
            "주의사항",
            "만 7세 이상",
            "준비물 없음",
            "취소 정책",
            CONFIRMED_AT
        ));
        ContentSession session = new ContentSession(
            content,
            region,
            SESSION_STARTS_AT,
            SESSION_ENDS_AT,
            CHECKIN_OPEN_AT,
            CHECKIN_CLOSE_AT,
            20
        );
        session.approve(operator, CONFIRMED_AT);
        return new Fixture(
            region,
            operator,
            user,
            content,
            contentSessionRepository.saveAndFlush(session)
        );
    }

    private Reservation saveReservation(
        Fixture fixture,
        ReservationStatus status,
        String label
    ) {
        CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            fixture.region(),
            fixture.session(),
            fixture.user(),
            1,
            CapacityHoldStatus.CONSUMED,
            SESSION_STARTS_AT,
            CONFIRMED_AT,
            null,
            null,
            CONFIRMED_AT
        ));
        Instant cancelledAt = status == ReservationStatus.CANCELLED ? CANCELLED_AT : null;
        String cancellationReason = status == ReservationStatus.CANCELLED ? "방문자 요청" : null;
        Instant expiredAt = status == ReservationStatus.EXPIRED ? EXPIRED_AT : null;
        return reservationRepository.saveAndFlush(new Reservation(
            "R-" + label + '-' + UUID.randomUUID(),
            "qr-" + label + '-' + UUID.randomUUID(),
            fixture.region(),
            hold,
            fixture.session(),
            fixture.user(),
            status,
            CONFIRMED_AT,
            cancelledAt,
            cancellationReason,
            expiredAt,
            null
        ));
    }

    private Visit saveVisit(Fixture fixture, Reservation reservation) {
        return visitRepository.saveAndFlush(new Visit(
            fixture.region(),
            reservation,
            fixture.user(),
            fixture.content(),
            fixture.session(),
            fixture.operator(),
            CheckinMethod.QR,
            CHECKED_AT
        ));
    }

    private AppUser saveUser(AppUserStatus status, String name, String phone) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            "user-" + suffix + "@example.com",
            "hashed-password",
            name,
            phone,
            status
        ));
    }

    private void assertReadDoesNotChangeState(
        Fixture fixture,
        List<Reservation> reservations,
        List<Visit> visits
    ) {
        entityManager.flush();
        entityManager.clear();

        assertThat(contentRepository.findById(fixture.content().getContentId()))
            .hasValueSatisfying(content -> {
                assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
                assertThat(content.getTitle()).isEqualTo("김해 문화 체험");
            });
        assertThat(contentSessionRepository.findById(fixture.session().getSessionId()))
            .hasValueSatisfying(session -> {
                assertThat(session.getRemainingCapacity()).isEqualTo(20);
                assertThat(session.getStartsAt()).isEqualTo(SESSION_STARTS_AT);
                assertThat(session.getEndsAt()).isEqualTo(SESSION_ENDS_AT);
            });
        assertThat(capacityHoldRepository.findAll())
            .allSatisfy(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.CONSUMED));
        assertThat(reservations).allSatisfy(reservation -> assertThat(
            reservationRepository.findById(reservation.getReservationId())
        ).hasValueSatisfying(current -> {
            assertThat(current.getStatus()).isEqualTo(reservation.getStatus());
            assertThat(current.getConfirmedAt()).isEqualTo(reservation.getConfirmedAt());
            assertThat(current.getQrReference()).isEqualTo(reservation.getQrReference());
        }));
        assertThat(visitRepository.findAll()).hasSize(visits.size());
        assertThat(visits).allSatisfy(visit -> assertThat(visitRepository.findById(visit.getVisitId()))
            .hasValueSatisfying(current -> assertThat(current.getCheckedAt()).isEqualTo(visit.getCheckedAt())));
        assertThat(auditEventRepository.findAll()).isEmpty();
    }

    private void assertReviewsUnchanged(List<ReviewState> expectedReviews) {
        entityManager.flush();
        entityManager.clear();

        List<ReviewState> actualReviews = reviewRepository.findAll().stream()
            .map(ReviewState::from)
            .toList();

        assertThat(actualReviews).containsExactlyInAnyOrderElementsOf(expectedReviews);
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, user.getUserId());
    }

    private record Fixture(
        Region region,
        AppUser operator,
        AppUser user,
        Content content,
        ContentSession session
    ) {
    }

    private record ReviewState(
        Long reviewId,
        ReviewStatus status,
        Integer rating,
        String reviewText,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
    ) {

        private static ReviewState from(Review review) {
            return new ReviewState(
                review.getReviewId(),
                review.getStatus(),
                review.getRating(),
                review.getReviewText(),
                review.getCreatedAt(),
                review.getUpdatedAt(),
                review.getDeletedAt()
            );
        }
    }
}
