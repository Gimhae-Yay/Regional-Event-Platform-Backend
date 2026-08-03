package io.regionevent.regioneventbackend.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
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
import io.regionevent.regioneventbackend.domain.review.dto.CreateVisitReviewRequest;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CreateVisitReviewUseCaseMySqlIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42");

    private final CreateVisitReviewUseCase createVisitReviewUseCase;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RegionRepository regionRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final VisitRepository visitRepository;
    private final AuditEventRepository auditEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    CreateVisitReviewUseCaseMySqlIntegrationTest(
        CreateVisitReviewUseCase createVisitReviewUseCase,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        RegionRepository regionRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        VisitRepository visitRepository,
        AuditEventRepository auditEventRepository,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.createVisitReviewUseCase = createVisitReviewUseCase;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.regionRepository = regionRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.visitRepository = visitRepository;
        this.auditEventRepository = auditEventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    @Timeout(10)
    void create_sameVisitConcurrently_createsOnlyOneReview() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch startSignal = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            List<Future<ErrorCode>> futures = List.of(
                executorService.submit(() -> createReview(fixture, startSignal)),
                executorService.submit(() -> createReview(fixture, startSignal))
            );
            startSignal.countDown();

            List<ErrorCode> results = Arrays.asList(futures.get(0).get(), futures.get(1).get());

            assertThat(results).containsExactlyInAnyOrder(null, ErrorCode.INVALID_INPUT);
            assertThat(countReviews(fixture.visit().getVisitId())).isOne();
        }
    }

    @Test
    @Timeout(10)
    void create_whenWithdrawalCommitsFirst_returnsForbiddenWithoutReview() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch withdrawalReady = new CountDownLatch(1);
        CountDownLatch releaseWithdrawal = new CountDownLatch(1);
        long failureAuditCountBefore = countReviewFailureAudits();

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<?> withdrawal = executorService.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                appUserRepository.findByIdForUpdate(fixture.user().getUserId()).orElseThrow();
                jdbcTemplate.update(
                    "UPDATE app_user SET status = 'WITHDRAWING' WHERE user_id = ?",
                    fixture.user().getUserId()
                );
                withdrawalReady.countDown();
                await(releaseWithdrawal);
            }));
            assertThat(withdrawalReady.await(3, TimeUnit.SECONDS)).isTrue();

            Future<ErrorCode> review = executorService.submit(() -> createReview(fixture, new CountDownLatch(0)));
            assertThat(review.isDone()).isFalse();

            releaseWithdrawal.countDown();
            withdrawal.get(3, TimeUnit.SECONDS);

            assertThat(review.get(3, TimeUnit.SECONDS)).isEqualTo(ErrorCode.FORBIDDEN);
        }

        assertThat(countReviews(fixture.visit().getVisitId())).isZero();
        assertThat(countReviewFailureAudits()).isEqualTo(failureAuditCountBefore + 1);
    }

    private ErrorCode createReview(Fixture fixture, CountDownLatch startSignal) {
        await(startSignal);
        try {
            createVisitReviewUseCase.create(
                fixture.user().getUserId(),
                fixture.visit().getVisitId(),
                new CreateVisitReviewRequest(5, "MySQL 동시성 후기"),
                UUID.randomUUID()
            );
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser user = saveUser("visitor-" + suffix + "@example.com", true);
        AppUser operator = saveUser("operator-" + suffix + "@example.com", false);
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
            Instant.now()
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
        session = contentSessionRepository.saveAndFlush(session);
        CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            user,
            1,
            CapacityHoldStatus.CONSUMED,
            Instant.now(),
            Instant.now().plusSeconds(60),
            null,
            null
        ));
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "R-" + suffix,
            UUID.randomUUID().toString(),
            region,
            hold,
            session,
            user,
            ReservationStatus.CONFIRMED,
            Instant.now(),
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
            session,
            operator,
            CheckinMethod.QR,
            Instant.now()
        ));
        return new Fixture(user, visit);
    }

    private AppUser saveUser(String loginIdentifier, boolean assignVisitorRole) {
        AppUser user = appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "방문자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        if (assignVisitorRole) {
            userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.VISITOR, null));
        }
        return user;
    }

    private long countReviewFailureAudits() {
        return auditEventRepository.findAll().stream()
            .filter(event -> event.getTargetType() == AuditEventTargetType.REVIEW)
            .filter(event -> event.getResult() == AuditEventResult.FAILURE)
            .count();
    }

    private long countReviews(Long visitId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM review WHERE visit_id = ?",
            Long.class,
            visitId
        );
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent test latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent test interrupted", exception);
        }
    }

    private record Fixture(AppUser user, Visit visit) {
    }
}
