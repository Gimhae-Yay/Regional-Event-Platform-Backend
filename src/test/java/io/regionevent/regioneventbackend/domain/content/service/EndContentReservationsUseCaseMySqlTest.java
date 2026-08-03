package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest(properties = {
    "reservation.hold-termination.initial-delay=PT24H",
    "reservation.no-show-completion.initial-delay=PT24H"
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EndContentReservationsUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final int SESSION_CAPACITY = 10;

    private final EndContentReservationsUseCase endContentReservationsUseCase;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentLogRepository contentLogRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    EndContentReservationsUseCaseMySqlTest(
        EndContentReservationsUseCase endContentReservationsUseCase,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        ContentLogRepository contentLogRepository,
        CapacityHoldRepository capacityHoldRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.endContentReservationsUseCase = endContentReservationsUseCase;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.contentLogRepository = contentLogRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.jdbcTemplate = jdbcTemplate;
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    void 종료는_활성홀드를_무효화하고_정원을_한번만_복구한다() {
        Fixture fixture = createFixture();

        EndContentReservationsResult result = endContentReservationsUseCase.end(
            fixture.adminId(),
            fixture.contentId(),
            UUID.randomUUID()
        );

        assertThat(result.status()).isEqualTo(ContentStatus.ENDED);
        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.ENDED));
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED, ContentLogStatus.ENDED);
        assertThat(capacityHoldRepository.findById(fixture.firstHoldId()))
            .hasValueSatisfying(this::assertInvalidated);
        assertThat(capacityHoldRepository.findById(fixture.secondHoldId()))
            .hasValueSatisfying(this::assertInvalidated);
        assertThat(contentSessionRepository.findById(fixture.firstSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(SESSION_CAPACITY));
        assertThat(contentSessionRepository.findById(fixture.secondSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(SESSION_CAPACITY));
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()))
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT);
                assertThat(auditEvent.getPreviousState()).isEqualTo("PUBLISHED");
                assertThat(auditEvent.getNextState()).isEqualTo("ENDED");
                assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                    .hasValueSatisfying(actorLink ->
                        assertThat(actorLink.getActor().getUserId()).isEqualTo(fixture.adminId())
                    );
            });
    }

    @Test
    @Timeout(10)
    void 동시_종료_요청에서도_로그와_감사와_정원복구는_한번만_발생한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<EndContentReservationsResult> results;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<EndContentReservationsResult> first = executorService.submit(
                () -> endAfterStart(fixture, ready, start)
            );
            Future<EndContentReservationsResult> second = executorService.submit(
                () -> endAfterStart(fixture, ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }

        assertThat(results).extracting(EndContentReservationsResult::status)
            .containsOnly(ContentStatus.ENDED);
        assertThat(results).extracting(EndContentReservationsResult::endedAt)
            .containsOnly(results.getFirst().endedAt());
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED, ContentLogStatus.ENDED);
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()))
            .hasSize(1);
        assertThat(capacityHoldRepository.findById(fixture.firstHoldId()))
            .hasValueSatisfying(this::assertInvalidated);
        assertThat(contentSessionRepository.findById(fixture.firstSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(SESSION_CAPACITY));
    }

    private EndContentReservationsResult endAfterStart(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        return endContentReservationsUseCase.end(
            fixture.adminId(),
            fixture.contentId(),
            UUID.randomUUID()
        );
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant now = Instant.now();
            Region region = regionRepository.save(new Region("R" + suffix, "김해시", true));
            AppUser admin = saveUser("admin-" + suffix, AppUserStatus.ACTIVE);
            userRoleAssignmentRepository.save(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
            AppUser operator = saveUser("operator-" + suffix, AppUserStatus.ACTIVE);
            AppUser visitor = saveUser("visitor-" + suffix, AppUserStatus.ACTIVE);
            Content content = contentRepository.save(new Content(
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
                now.minusSeconds(86_400)
            ));
            contentLogRepository.save(new ContentLog(
                content,
                operator,
                ContentLogStatus.PUBLISHED,
                null,
                now.minusSeconds(86_400)
            ));
            ContentSession firstSession = saveCompletedSession(content, region, admin, now.plusSeconds(3_600));
            ContentSession secondSession = saveCancelledSession(content, region, admin, now.plusSeconds(10_800));
            CapacityHold firstHold = saveActiveHold(region, firstSession, visitor, 2, now);
            CapacityHold secondHold = saveActiveHold(region, secondSession, visitor, 1, now);
            return new Fixture(
                admin.getUserId(),
                content.getContentId(),
                firstSession.getSessionId(),
                secondSession.getSessionId(),
                firstHold.getHoldId(),
                secondHold.getHoldId()
            );
        });
    }

    private ContentSession saveCompletedSession(
        Content content,
        Region region,
        AppUser admin,
        Instant startsAt
    ) {
        ContentSession session = newSession(content, region, startsAt);
        session.approve(admin, startsAt.minusSeconds(3_600));
        session.complete(startsAt.minusSeconds(1_800));
        return contentSessionRepository.save(session);
    }

    private ContentSession saveCancelledSession(
        Content content,
        Region region,
        AppUser admin,
        Instant startsAt
    ) {
        ContentSession session = newSession(content, region, startsAt);
        session.approve(admin, startsAt.minusSeconds(3_600));
        session.cancel(admin, startsAt.minusSeconds(1_800), "정상 종료 전 회차 취소");
        return contentSessionRepository.save(session);
    }

    private ContentSession newSession(
        Content content,
        Region region,
        Instant startsAt
    ) {
        return new ContentSession(
            content,
            region,
            startsAt,
            startsAt.plusSeconds(10_800),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(9_000),
            SESSION_CAPACITY
        );
    }

    private CapacityHold saveActiveHold(
        Region region,
        ContentSession session,
        AppUser visitor,
        int quantity,
        Instant now
    ) {
        jdbcTemplate.update(
            "UPDATE content_session SET remaining_capacity = remaining_capacity - ? WHERE session_id = ?",
            quantity,
            session.getSessionId()
        );
        return capacityHoldRepository.save(new CapacityHold(
            region,
            session,
            visitor,
            quantity,
            CapacityHoldStatus.ACTIVE,
            now.plusSeconds(600),
            null,
            null,
            null,
            now
        ));
    }

    private AppUser saveUser(String identifierPrefix, AppUserStatus status) {
        return appUserRepository.save(new AppUser(
            identifierPrefix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            status
        ));
    }

    private void assertInvalidated(CapacityHold capacityHold) {
        assertThat(capacityHold.getStatus()).isEqualTo(CapacityHoldStatus.INVALIDATED);
        assertThat(capacityHold.getInvalidationReason()).isEqualTo("CONTENT_ENDED");
        assertThat(capacityHold.getTerminalAt()).isNotNull();
        assertThat(capacityHold.getCapacityReleasedAt()).isNotNull();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent content ending did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent content ending was interrupted", exception);
        }
    }

    private record Fixture(
        Long adminId,
        Long contentId,
        Long firstSessionId,
        Long secondSessionId,
        Long firstHoldId,
        Long secondHoldId
    ) {
    }
}
