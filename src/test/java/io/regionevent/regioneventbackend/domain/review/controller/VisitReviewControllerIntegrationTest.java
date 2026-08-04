package io.regionevent.regioneventbackend.domain.review.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
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
import io.regionevent.regioneventbackend.domain.review.dto.CreateVisitReviewRequest;
import io.regionevent.regioneventbackend.domain.review.dto.UpdateReviewRequest;
import io.regionevent.regioneventbackend.domain.review.entity.Review;
import io.regionevent.regioneventbackend.domain.review.entity.ReviewStatus;
import io.regionevent.regioneventbackend.domain.review.repository.ReviewRepository;
import io.regionevent.regioneventbackend.domain.review.service.CreateVisitReviewUseCase;
import io.regionevent.regioneventbackend.domain.review.service.DeleteReviewUseCase;
import io.regionevent.regioneventbackend.domain.review.service.ReviewService;
import io.regionevent.regioneventbackend.domain.review.service.UpdateReviewUseCase;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;
import io.regionevent.regioneventbackend.domain.visit.service.VisitService;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
class VisitReviewControllerIntegrationTest {

    private final MockMvc mockMvc;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RegionRepository regionRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final VisitRepository visitRepository;
    private final ReviewRepository reviewRepository;
    private final AuditEventRepository auditEventRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final JdbcTemplate jdbcTemplate;
    private final AppUserService appUserService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final VisitService visitService;
    private final ReviewService reviewService;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    VisitReviewControllerIntegrationTest(
        MockMvc mockMvc,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        RegionRepository regionRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        VisitRepository visitRepository,
        ReviewRepository reviewRepository,
        AuditEventRepository auditEventRepository,
        JwtAccessTokenService jwtAccessTokenService,
        JdbcTemplate jdbcTemplate,
        AppUserService appUserService,
        UserRoleAssignmentService userRoleAssignmentService,
        VisitService visitService,
        ReviewService reviewService,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        Clock clock,
        PlatformTransactionManager transactionManager
    ) {
        this.mockMvc = mockMvc;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.regionRepository = regionRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.visitRepository = visitRepository;
        this.reviewRepository = reviewRepository;
        this.auditEventRepository = auditEventRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.jdbcTemplate = jdbcTemplate;
        this.appUserService = appUserService;
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.visitService = visitService;
        this.reviewService = reviewService;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void createReview_createsPublishedReviewAndSuccessAudit() throws Exception {
        Fixture fixture = createFixture(true);

        performCreate(fixture.user(), fixture.visit().getVisitId(), 5, "  즐거운 체험이었습니다.  ")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("후기 작성에 성공했습니다."))
            .andExpect(jsonPath("$.data.visitId").value(fixture.visit().getVisitId().toString()))
            .andExpect(jsonPath("$.data.contentId").value(fixture.content().getContentId().toString()))
            .andExpect(jsonPath("$.data.rating").value(5))
            .andExpect(jsonPath("$.data.reviewText").value("즐거운 체험이었습니다."));

        assertThat(countReviews(fixture.visit().getVisitId())).isOne();
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getTargetType() == AuditEventTargetType.REVIEW)
            .filteredOn(event -> event.getResult() == AuditEventResult.SUCCESS)
            .anySatisfy(event -> assertThat(event.getNextState()).isEqualTo(ReviewStatus.PUBLISHED.name()));
    }

    @Test
    void updateReview_updatesProvidedFieldAndRecordsSuccessAudit() throws Exception {
        Fixture fixture = createFixture(true);
        Review review = createPublishedReview(fixture, 5, "수정 전 후기");

        performUpdate(fixture.user(), review.getReviewId(), """
            {
              "reviewText": "  수정한 후기  "
            }
            """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("후기 수정에 성공했습니다."))
            .andExpect(jsonPath("$.data.reviewId").value(review.getReviewId().toString()))
            .andExpect(jsonPath("$.data.rating").value(5))
            .andExpect(jsonPath("$.data.reviewText").value("수정한 후기"));

        Review updatedReview = reviewRepository.findById(review.getReviewId()).orElseThrow();
        assertThat(updatedReview.getRating()).isEqualTo(5);
        assertThat(updatedReview.getReviewText()).isEqualTo("수정한 후기");
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getTargetType() == AuditEventTargetType.REVIEW)
            .filteredOn(event -> event.getResult() == AuditEventResult.SUCCESS)
            .anySatisfy(event -> assertThat(event.getTargetId()).isEqualTo(review.getReviewId()));
    }

    @Test
    void updateReview_whenNoFieldIsProvided_returnsInvalidInputWithoutMutation() throws Exception {
        Fixture fixture = createFixture(true);
        Review review = createPublishedReview(fixture, 5, "수정 전 후기");
        long failureAuditCountBefore = countReviewFailureAudits();

        performUpdate(fixture.user(), review.getReviewId(), "{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        Review unchangedReview = reviewRepository.findById(review.getReviewId()).orElseThrow();
        assertThat(unchangedReview.getRating()).isEqualTo(5);
        assertThat(unchangedReview.getReviewText()).isEqualTo("수정 전 후기");
        assertThat(countReviewFailureAudits()).isEqualTo(failureAuditCountBefore);
    }

    @Test
    void updateReview_whenAuthorDiffers_returnsForbiddenWithoutMutation() throws Exception {
        Fixture fixture = createFixture(true);
        Review review = createPublishedReview(fixture, 5, "수정 전 후기");
        AppUser anotherVisitor = saveUser("another-visitor-" + System.nanoTime() + "@example.com", true);

        performUpdate(anotherVisitor, review.getReviewId(), """
            {
              "rating": 4
            }
            """)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(reviewRepository.findById(review.getReviewId()).orElseThrow().getRating()).isEqualTo(5);
    }

    @Test
    void updateReview_whenThirtyDaysHaveElapsed_returnsForbiddenWithoutMutation() throws Exception {
        Fixture fixture = createFixture(true);
        Review review = createPublishedReview(fixture, 5, "수정 전 후기");
        long failureAuditCountBefore = countReviewFailureAudits();
        jdbcTemplate.update(
            "UPDATE review SET created_at = DATEADD('DAY', -30, CURRENT_TIMESTAMP(6)) WHERE review_id = ?",
            review.getReviewId()
        );

        performUpdate(fixture.user(), review.getReviewId(), """
            {
              "rating": 4
            }
            """)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(reviewRepository.findById(review.getReviewId()).orElseThrow().getRating()).isEqualTo(5);
        assertThat(countReviewFailureAudits()).isEqualTo(failureAuditCountBefore + 1);
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getTargetType() == AuditEventTargetType.REVIEW)
            .filteredOn(event -> event.getResult() == AuditEventResult.FAILURE)
            .filteredOn(event -> review.getReviewId().equals(event.getTargetId()))
            .anySatisfy(event -> assertThat(event.getRegion().getRegionId())
                .isEqualTo(fixture.region().getRegionId()));
    }

    @Test
    void updateReview_whenSuccessAuditRecordingFails_rollsBackReview() {
        Fixture fixture = createFixture(true);
        Review review = createPublishedReview(fixture, 5, "수정 전 후기");
        long failureAuditCountBefore = countReviewFailureAudits();
        RecordAuditEventUseCase rejectingAuditEventUseCase = mock(RecordAuditEventUseCase.class);
        doThrow(new IllegalStateException("audit storage failure"))
            .when(rejectingAuditEventUseCase)
            .record(any(AuditEventCommand.class));
        UpdateReviewUseCase useCase = new UpdateReviewUseCase(
            appUserService,
            userRoleAssignmentService,
            reviewService,
            rejectingAuditEventUseCase,
            recordFailedAuditEventUseCase
        );
        UpdateReviewRequest request = new UpdateReviewRequest();
        request.setRating(4);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(transactionStatus ->
            useCase.update(
                fixture.user().getUserId(),
                review.getReviewId(),
                request,
                UUID.randomUUID()
            )
        )).isInstanceOf(IllegalStateException.class);

        assertThat(reviewRepository.findById(review.getReviewId()).orElseThrow().getRating()).isEqualTo(5);
        assertThat(countReviewFailureAudits()).isEqualTo(failureAuditCountBefore + 1);
    }

    @Test
    void createReview_whenUserHasNoVisitorRole_returnsForbidden() throws Exception {
        Fixture fixture = createFixture(false);
        long failureAuditCountBefore = countReviewFailureAudits();

        performCreate(fixture.user(), fixture.visit().getVisitId(), 5, "후기")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(countReviews(fixture.visit().getVisitId())).isZero();
        assertThat(countReviewFailureAudits()).isEqualTo(failureAuditCountBefore + 1);
    }

    @Test
    void createReview_whenMemberIsNotActive_returnsForbidden() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.WITHDRAWING, true);
        long failureAuditCountBefore = countReviewFailureAudits();

        performCreate(fixture.user(), fixture.visit().getVisitId(), 5, "후기")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(countReviews(fixture.visit().getVisitId())).isZero();
        assertThat(countReviewFailureAudits()).isEqualTo(failureAuditCountBefore + 1);
    }

    @Test
    void createReview_whenVisitOwnerDiffers_returnsForbiddenAndRecordsFailureAudit() throws Exception {
        Fixture fixture = createFixture(true);
        AppUser anotherVisitor = saveUser("another-visitor-" + System.nanoTime() + "@example.com", true);
        long failureAuditCountBefore = countReviewFailureAudits();

        performCreate(anotherVisitor, fixture.visit().getVisitId(), 5, "후기")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(countReviews(fixture.visit().getVisitId())).isZero();
        assertThat(countReviewFailureAudits()).isEqualTo(failureAuditCountBefore + 1);
    }

    @Test
    void createReview_whenVisitDoesNotExist_returnsNotFoundAndRecordsFailureAudit() throws Exception {
        Fixture fixture = createFixture(true);
        long failureAuditCountBefore = countReviewFailureAudits();

        performCreate(fixture.user(), fixture.visit().getVisitId() + 1_000_000, 5, "후기")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(countReviewFailureAudits()).isEqualTo(failureAuditCountBefore + 1);
    }

    @Test
    void createReview_whenReviewAlreadyDeleted_rejectsRecreation() throws Exception {
        Fixture fixture = createFixture(true);
        reviewRepository.saveAndFlush(new Review(
            fixture.region(),
            fixture.visit(),
            fixture.user(),
            fixture.content(),
            5,
            "삭제된 후기",
            ReviewStatus.DELETED,
            Instant.now()
        ));

        performCreate(fixture.user(), fixture.visit().getVisitId(), 5, "새 후기")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(countReviews(fixture.visit().getVisitId())).isOne();
    }

    @Test
    void createReview_whenDeletedReviewOriginalIsPurged_rejectsRecreation() throws Exception {
        Fixture fixture = createFixture(true);
        reviewRepository.saveAndFlush(new Review(
            fixture.region(),
            fixture.visit(),
            fixture.user(),
            fixture.content(),
            null,
            null,
            ReviewStatus.DELETED,
            Instant.now()
        ));

        performCreate(fixture.user(), fixture.visit().getVisitId(), 5, "새 후기")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(countReviews(fixture.visit().getVisitId())).isOne();
    }

    @Test
    void createReview_whenSuccessAuditRecordingFails_rollsBackReview() throws Exception {
        Fixture fixture = createFixture(true);
        long failureAuditCountBefore = countReviewFailureAudits();
        RecordAuditEventUseCase rejectingAuditEventUseCase = mock(RecordAuditEventUseCase.class);
        doThrow(new IllegalStateException("audit storage failure"))
            .when(rejectingAuditEventUseCase)
            .record(any(AuditEventCommand.class));
        CreateVisitReviewUseCase useCase = new CreateVisitReviewUseCase(
            appUserService,
            userRoleAssignmentService,
            visitService,
            reviewService,
            rejectingAuditEventUseCase,
            recordFailedAuditEventUseCase
        );

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(transactionStatus ->
            useCase.create(
                fixture.user().getUserId(),
                fixture.visit().getVisitId(),
                new CreateVisitReviewRequest(5, "후기"),
                UUID.randomUUID()
            )
        )).isInstanceOf(IllegalStateException.class);

        assertThat(countReviews(fixture.visit().getVisitId())).isZero();
        assertThat(countReviewFailureAudits()).isEqualTo(failureAuditCountBefore + 1);
    }

    @Test
    void createReview_whenPathOrBodyIsInvalid_doesNotCreateReviewOrFailureAudit() throws Exception {
        Fixture fixture = createFixture(true);
        long failureAuditCountBefore = countReviewFailureAudits();

        mockMvc.perform(post("/api/v1/visits/not-a-number/reviews")
                .header("Authorization", "Bearer " + jwtAccessTokenService.issue(fixture.user().getUserId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "rating": 5,
                      "reviewText": "후기"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        performCreate(fixture.user(), fixture.visit().getVisitId(), 0, "후기")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(countReviews(fixture.visit().getVisitId())).isZero();
        assertThat(countReviewFailureAudits()).isEqualTo(failureAuditCountBefore);
    }

    @Test
    void createReview_whenPathIdFormatIsInvalid_returnsInputErrorWithoutFailureAudit() throws Exception {
        Fixture fixture = createFixture(true);
        long failureAuditCountBefore = countReviewFailureAudits();

        for (String invalidVisitId : List.of("01", "+1", "0", "-1")) {
            performCreate(fixture.user(), invalidVisitId, 5, "후기")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        performCreate(fixture.user(), "9223372036854775808", 5, "후기")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        assertThat(countReviews(fixture.visit().getVisitId())).isZero();
        assertThat(countReviewFailureAudits()).isEqualTo(failureAuditCountBefore);
    }

    @Test
    void createReview_whenRequestBodyFieldTypeIsInvalid_returnsInvalidTypeWithoutFailureAudit() throws Exception {
        Fixture fixture = createFixture(true);
        long failureAuditCountBefore = countReviewFailureAudits();

        mockMvc.perform(post("/api/v1/visits/{visitId}/reviews", fixture.visit().getVisitId())
                .header("Authorization", "Bearer " + jwtAccessTokenService.issue(fixture.user().getUserId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "rating": "5",
                      "reviewText": "후기"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        mockMvc.perform(post("/api/v1/visits/{visitId}/reviews", fixture.visit().getVisitId())
                .header("Authorization", "Bearer " + jwtAccessTokenService.issue(fixture.user().getUserId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "rating": 5,
                      "reviewText": 1
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        assertThat(countReviews(fixture.visit().getVisitId())).isZero();
        assertThat(countReviewFailureAudits()).isEqualTo(failureAuditCountBefore);
    }

    @Test
    void createReview_whenUnauthenticatedOrJsonIsMalformed_doesNotCreateReview() throws Exception {
        Fixture fixture = createFixture(true);

        mockMvc.perform(post("/api/v1/visits/{visitId}/reviews", fixture.visit().getVisitId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "rating": 5,
                      "reviewText": "후기"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/v1/visits/{visitId}/reviews", fixture.visit().getVisitId())
                .header("Authorization", "Bearer " + jwtAccessTokenService.issue(fixture.user().getUserId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));

        assertThat(countReviews(fixture.visit().getVisitId())).isZero();
    }

    @Test
    void deleteReview_deletesPublishedReviewAndRecordsSuccessAudit() throws Exception {
        Fixture fixture = createFixture(true);
        Review review = savePublishedReview(fixture);

        performDelete(fixture.user(), review.getReviewId())
            .andExpect(status().isNoContent());

        assertThat(reviewRepository.findById(review.getReviewId()))
            .hasValueSatisfying(deletedReview -> {
                assertThat(deletedReview.getStatus()).isEqualTo(ReviewStatus.DELETED);
                assertThat(deletedReview.getDeletedAt()).isNotNull();
            });
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getTargetType() == AuditEventTargetType.REVIEW)
            .filteredOn(event -> review.getReviewId().equals(event.getTargetId()))
            .filteredOn(event -> event.getResult() == AuditEventResult.SUCCESS)
            .anySatisfy(event -> {
                assertThat(event.getPreviousState()).isEqualTo(ReviewStatus.PUBLISHED.name());
                assertThat(event.getNextState()).isEqualTo(ReviewStatus.DELETED.name());
            });
    }

    @Test
    void deleteReview_whenPathIsInvalid_doesNotChangeReviewOrRecordAudit() throws Exception {
        Fixture fixture = createFixture(true);
        Review review = savePublishedReview(fixture);
        long auditCountBefore = auditEventRepository.count();

        performDelete(fixture.user(), "not-a-number")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        assertThat(reviewRepository.findById(review.getReviewId()))
            .hasValueSatisfying(savedReview -> assertThat(savedReview.getStatus()).isEqualTo(ReviewStatus.PUBLISHED));
        assertThat(auditEventRepository.count()).isEqualTo(auditCountBefore);
    }

    @Test
    void deleteReview_whenUnauthenticatedOrNotAuthor_returnsForbiddenWithoutDeletingAndRecordsActorLink() throws Exception {
        Fixture fixture = createFixture(true);
        Review review = savePublishedReview(fixture);
        AppUser anotherUser = saveUser("another-visitor-" + System.nanoTime() + "@example.com", true);

        mockMvc.perform(delete("/api/v1/reviews/{reviewId}", review.getReviewId()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        performDelete(anotherUser, review.getReviewId())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(reviewRepository.findById(review.getReviewId()))
            .hasValueSatisfying(savedReview -> assertThat(savedReview.getStatus()).isEqualTo(ReviewStatus.PUBLISHED));
        Long failureAuditEventId = auditEventRepository.findAll()
            .stream()
            .filter(event -> event.getTargetType() == AuditEventTargetType.REVIEW)
            .filter(event -> review.getReviewId().equals(event.getTargetId()))
            .filter(event -> event.getResult() == AuditEventResult.FAILURE)
            .findFirst()
            .orElseThrow()
            .getAuditEventId();

        assertThat(jdbcTemplate.queryForObject(
            "SELECT user_id FROM audit_event_actor_link WHERE audit_event_id = ?",
            Long.class,
            failureAuditEventId
        )).isEqualTo(anotherUser.getUserId());
    }

    @Test
    void deleteReview_whenAuthorIsWithdrawing_returnsForbiddenWithoutDeleting() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.WITHDRAWING, true);
        Review review = savePublishedReview(fixture);

        performDelete(fixture.user(), review.getReviewId())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(reviewRepository.findById(review.getReviewId()))
            .hasValueSatisfying(savedReview -> assertThat(savedReview.getStatus()).isEqualTo(ReviewStatus.PUBLISHED));
    }

    @Test
    void deleteReview_whenAlreadyDeleted_returnsNotFoundWithoutNewAudit() throws Exception {
        Fixture fixture = createFixture(true);
        Review review = reviewRepository.saveAndFlush(new Review(
            fixture.region(),
            fixture.visit(),
            fixture.user(),
            fixture.content(),
            5,
            "이미 삭제된 후기",
            ReviewStatus.DELETED,
            Instant.now()
        ));
        long auditCountBefore = auditEventRepository.count();

        performDelete(fixture.user(), review.getReviewId())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(auditEventRepository.count()).isEqualTo(auditCountBefore);
    }

    @Test
    void deleteReview_whenSuccessAuditRecordingFails_rollsBackDeletion() {
        Fixture fixture = createFixture(true);
        Review review = savePublishedReview(fixture);
        RecordAuditEventUseCase rejectingAuditEventUseCase = mock(RecordAuditEventUseCase.class);
        doThrow(new IllegalStateException("audit storage failure"))
            .when(rejectingAuditEventUseCase)
            .record(any(AuditEventCommand.class));
        DeleteReviewUseCase useCase = new DeleteReviewUseCase(
            appUserService,
            userRoleAssignmentService,
            reviewService,
            rejectingAuditEventUseCase,
            recordFailedAuditEventUseCase,
            clock
        );

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(transactionStatus ->
            useCase.delete(fixture.user().getUserId(), review.getReviewId(), UUID.randomUUID())
        )).isInstanceOf(IllegalStateException.class);

        assertThat(reviewRepository.findById(review.getReviewId()))
            .hasValueSatisfying(savedReview -> assertThat(savedReview.getStatus()).isEqualTo(ReviewStatus.PUBLISHED));
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> review.getReviewId().equals(event.getTargetId()))
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getResult()).isEqualTo(AuditEventResult.FAILURE);
                assertThat(event.getPreviousState()).isEqualTo(ReviewStatus.PUBLISHED.name());
                assertThat(event.getNextState()).isNull();
            });
    }

    private org.springframework.test.web.servlet.ResultActions performCreate(
        AppUser user,
        Long visitId,
        int rating,
        String reviewText
    ) throws Exception {
        return performCreate(user, visitId.toString(), rating, reviewText);
    }

    private org.springframework.test.web.servlet.ResultActions performUpdate(
        AppUser user,
        Long reviewId,
        String requestBody
    ) throws Exception {
        return mockMvc.perform(patch("/api/v1/reviews/{reviewId}", reviewId)
            .header("Authorization", "Bearer " + jwtAccessTokenService.issue(user.getUserId()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody));
    }

    private Review createPublishedReview(Fixture fixture, int rating, String reviewText) {
        return reviewRepository.saveAndFlush(new Review(
            fixture.region(),
            fixture.visit(),
            fixture.user(),
            fixture.content(),
            rating,
            reviewText,
            ReviewStatus.PUBLISHED,
            null
        ));
    }

    private org.springframework.test.web.servlet.ResultActions performCreate(
        AppUser user,
        String visitId,
        int rating,
        String reviewText
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/visits/{visitId}/reviews", visitId)
            .header("Authorization", "Bearer " + jwtAccessTokenService.issue(user.getUserId()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "rating": %d,
                  "reviewText": "%s"
                }
            """.formatted(rating, reviewText)));
    }

    private org.springframework.test.web.servlet.ResultActions performDelete(
        AppUser user,
        Long reviewId
    ) throws Exception {
        return performDelete(user, reviewId.toString());
    }

    private org.springframework.test.web.servlet.ResultActions performDelete(
        AppUser user,
        String reviewId
    ) throws Exception {
        return mockMvc.perform(delete("/api/v1/reviews/{reviewId}", reviewId)
            .header("Authorization", "Bearer " + jwtAccessTokenService.issue(user.getUserId())));
    }

    private Review savePublishedReview(Fixture fixture) {
        return reviewRepository.saveAndFlush(new Review(
            fixture.region(),
            fixture.visit(),
            fixture.user(),
            fixture.content(),
            5,
            "삭제할 후기",
            ReviewStatus.PUBLISHED,
            null
        ));
    }

    private Fixture createFixture(boolean assignVisitorRole) {
        return createFixture(AppUserStatus.ACTIVE, assignVisitorRole);
    }

    private Fixture createFixture(AppUserStatus userStatus, boolean assignVisitorRole) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser operator = saveUser("operator-" + suffix + "@example.com", false);
        AppUser user = saveUser("visitor-" + suffix + "@example.com", userStatus, assignVisitorRole);
        AppUser checkinOperator = saveUser("checkin-operator-" + suffix + "@example.com", false);
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "지역 체험",
            "지역 체험 설명",
            "김해",
            "10:00~18:00",
            "055-1234-5678",
            "안전 수칙을 지켜주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 전까지 취소할 수 있습니다.",
            Instant.now().minusSeconds(60)
        ));
        ContentSession contentSession = new ContentSession(
            content,
            region,
            Instant.now().plusSeconds(3_600),
            Instant.now().plusSeconds(10_800),
            Instant.now().plusSeconds(1_800),
            Instant.now().plusSeconds(9_000),
            20
        );
        contentSession.approve(checkinOperator, Instant.now());
        contentSession = contentSessionRepository.saveAndFlush(contentSession);
        CapacityHold capacityHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            contentSession,
            user,
            1,
            CapacityHoldStatus.CONSUMED,
            Instant.now().minusSeconds(600),
            Instant.now().minusSeconds(300),
            null,
            null
        ));
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "R-" + suffix,
            UUID.randomUUID().toString(),
            region,
            capacityHold,
            contentSession,
            user,
            ReservationStatus.CONFIRMED,
            Instant.now().minusSeconds(300),
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
            Instant.now()
        ));
        return new Fixture(region, content, user, visit);
    }

    private AppUser saveUser(String loginIdentifier, boolean assignVisitorRole) {
        return saveUser(loginIdentifier, AppUserStatus.ACTIVE, assignVisitorRole);
    }

    private AppUser saveUser(
        String loginIdentifier,
        AppUserStatus status,
        boolean assignVisitorRole
    ) {
        AppUser user = appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "방문자",
            "010-1234-5678",
            status
        ));
        if (assignVisitorRole) {
            userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.VISITOR, null));
        }
        return user;
    }

    private long countReviews(Long visitId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM review WHERE visit_id = ?",
            Long.class,
            visitId
        );
    }

    private long countReviewFailureAudits() {
        return auditEventRepository.findAll()
            .stream()
            .filter(event -> event.getTargetType() == AuditEventTargetType.REVIEW)
            .filter(event -> event.getResult() == AuditEventResult.FAILURE)
            .count();
    }

    private record Fixture(
        Region region,
        Content content,
        AppUser user,
        Visit visit
    ) {
    }
}
