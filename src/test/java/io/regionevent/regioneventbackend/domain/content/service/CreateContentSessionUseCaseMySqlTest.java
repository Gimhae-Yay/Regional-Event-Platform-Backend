package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentSessionRequest;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest(properties = {
    "reservation.hold-termination.initial-delay=PT24H",
    "reservation.no-show-completion.initial-delay=PT24H"
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CreateContentSessionUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private final CreateContentSessionUseCase createContentSessionUseCase;
    private final EndContentReservationsUseCase endContentReservationsUseCase;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentLogRepository contentLogRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    CreateContentSessionUseCaseMySqlTest(
        CreateContentSessionUseCase createContentSessionUseCase,
        EndContentReservationsUseCase endContentReservationsUseCase,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        ContentLogRepository contentLogRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.createContentSessionUseCase = createContentSessionUseCase;
        this.endContentReservationsUseCase = endContentReservationsUseCase;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.contentLogRepository = contentLogRepository;
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
    void 회차를_먼저_생성하면_자동과_수동_콘텐츠_종료가_모두_진행되지_않는다() {
        Fixture fixture = createFixture();

        CreateContentSessionResult result = create(fixture);
        endContentReservationsUseCase.endBySystem(fixture.contentId(), UUID.randomUUID());

        assertThatThrownBy(() -> endContentReservationsUseCase.endByRegionAdmin(
            fixture.adminId(),
            fixture.contentId(),
            UUID.randomUUID()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_END_CONFLICT)
        );

        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED));
        assertThat(contentSessionRepository.findById(result.sessionId()))
            .hasValueSatisfying(session -> {
                assertThat(session.getStatus()).isEqualTo(ContentSessionStatus.PENDING);
                assertThat(session.getRemainingCapacity()).isEqualTo(session.getCapacity());
            });
        assertSuccessfulSessionAudit(fixture, result.sessionId());
    }

    @Test
    void 종료가_먼저_완료되면_회차와_성공_부수효과_없이_실패감사만_기록한다() {
        Fixture fixture = createFixture();
        UUID requestId = UUID.randomUUID();
        endContentReservationsUseCase.endBySystem(fixture.contentId(), UUID.randomUUID());

        assertThatThrownBy(() -> create(fixture, requestId))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );

        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.ENDED));
        assertThat(contentSessionRepository.findByContentContentIdAndStatusOrderByStartsAtAsc(
            fixture.contentId(),
            ContentSessionStatus.PENDING
        )).isEmpty();
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED, ContentLogStatus.ENDED);
        assertEndedCreationFailureAudit(fixture, requestId);
    }

    @Test
    @Timeout(10)
    void 수동종료가_콘텐츠_잠금을_먼저_대기하면_회차생성은_종료뒤_실패한다() throws Exception {
        Fixture fixture = createFixture();
        UUID requestId = UUID.randomUUID();
        CountDownLatch contentLocked = new CountDownLatch(1);
        CountDownLatch releaseContentLock = new CountDownLatch(1);

        try (ExecutorService executorService = Executors.newFixedThreadPool(3)) {
            Future<?> lockHolder = executorService.submit(
                () -> holdContentLock(fixture.contentId(), contentLocked, releaseContentLock)
            );
            assertThat(contentLocked.await(3, TimeUnit.SECONDS)).isTrue();

            Future<Attempt> endAttempt = executorService.submit(() -> endByRegionAdmin(fixture));
            assertThat(awaitContentLockWaitCount(1)).isTrue();

            Future<Attempt> createAttempt = executorService.submit(
                () -> createExpectingResult(fixture, requestId)
            );
            assertThat(awaitContentLockWaitCount(2)).isTrue();
            releaseContentLock.countDown();

            lockHolder.get(5, TimeUnit.SECONDS);
            assertThat(endAttempt.get(5, TimeUnit.SECONDS).errorCode()).isNull();
            assertThat(createAttempt.get(5, TimeUnit.SECONDS).errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        }

        List<ContentSession> pendingSessions = contentSessionRepository
            .findByContentContentIdAndStatusOrderByStartsAtAsc(fixture.contentId(), ContentSessionStatus.PENDING);
        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.ENDED));
        assertThat(pendingSessions).isEmpty();
        assertEndedCreationFailureAudit(fixture, requestId);
    }

    private CreateContentSessionResult create(Fixture fixture) {
        return create(fixture, UUID.randomUUID());
    }

    private CreateContentSessionResult create(Fixture fixture, UUID requestId) {
        Instant startsAt = Instant.now().plusSeconds(172_800);
        return createContentSessionUseCase.create(
            fixture.operatorId(),
            fixture.contentId(),
            request(startsAt),
            requestId
        );
    }

    private Attempt createExpectingResult(Fixture fixture, UUID requestId) {
        try {
            create(fixture, requestId);
            return new Attempt(null);
        } catch (BusinessException exception) {
            return new Attempt(exception.getErrorCode());
        }
    }

    private Attempt endByRegionAdmin(Fixture fixture) {
        try {
            endContentReservationsUseCase.endByRegionAdmin(
                fixture.adminId(),
                fixture.contentId(),
                UUID.randomUUID()
            );
            return new Attempt(null);
        } catch (BusinessException exception) {
            return new Attempt(exception.getErrorCode());
        }
    }

    private void holdContentLock(
        Long contentId,
        CountDownLatch contentLocked,
        CountDownLatch releaseContentLock
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            contentRepository.findByContentIdAndDeletedAtIsNull(contentId).orElseThrow();
            contentLocked.countDown();
            await(releaseContentLock);
        });
    }

    private boolean awaitContentLockWaitCount(int expectedCount) {
        for (int attempt = 0; attempt < 30; attempt++) {
            Integer waitingRequestCount = jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM information_schema.processlist
                    WHERE id <> CONNECTION_ID()
                        AND db = DATABASE()
                        AND command = 'Query'
                        AND LOWER(info) LIKE '%for update%'
                    """,
                Integer.class
            );
            if (waitingRequestCount != null && waitingRequestCount >= expectedCount) {
                return true;
            }
            awaitLockWaitInterval();
        }
        return false;
    }

    private void awaitLockWaitInterval() {
        try {
            TimeUnit.MILLISECONDS.sleep(100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("content lock wait confirmation was interrupted", exception);
        }
    }

    private CreateContentSessionRequest request(Instant startsAt) {
        return new CreateContentSessionRequest(
            OffsetDateTime.ofInstant(startsAt, ZoneOffset.ofHours(9)),
            OffsetDateTime.ofInstant(startsAt.plusSeconds(7_200), ZoneOffset.ofHours(9)),
            OffsetDateTime.ofInstant(startsAt.minusSeconds(1_800), ZoneOffset.ofHours(9)),
            OffsetDateTime.ofInstant(startsAt.plusSeconds(5_400), ZoneOffset.ofHours(9)),
            30
        );
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant now = Instant.now();
            Region region = regionRepository.save(new Region("R" + suffix, "김해시", true));
            AppUser admin = saveUser("admin-" + suffix);
            userRoleAssignmentRepository.save(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
            AppUser operator = saveUser("operator-" + suffix);
            userRoleAssignmentRepository.save(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
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
            contentSessionRepository.save(completedSession(content, region, admin, now));
            return new Fixture(
                admin.getUserId(),
                operator.getUserId(),
                region.getRegionId(),
                content.getContentId()
            );
        });
    }

    private ContentSession completedSession(Content content, Region region, AppUser admin, Instant now) {
        ContentSession session = new ContentSession(
            content,
            region,
            now.minusSeconds(14_400),
            now.minusSeconds(7_200),
            now.minusSeconds(16_200),
            now.minusSeconds(9_000),
            30
        );
        session.approve(admin, now.minusSeconds(18_000));
        session.complete(now.minusSeconds(3_600));
        return session;
    }

    private AppUser saveUser(String identifierPrefix) {
        return appUserRepository.save(new AppUser(
            identifierPrefix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private void assertSuccessfulSessionAudit(Fixture fixture, Long sessionId) {
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> sessionId.equals(auditEvent.getTargetId()))
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT_SESSION);
                assertThat(auditEvent.getNextState()).isEqualTo(ContentSessionStatus.PENDING.name());
                assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
                assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                    .hasValueSatisfying(link ->
                        assertThat(link.getActor().getUserId()).isEqualTo(fixture.operatorId())
                    );
            });
    }

    private void assertEndedCreationFailureAudit(Fixture fixture, UUID requestId) {
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()))
            .filteredOn(auditEvent -> auditEvent.getResult() == AuditEventResult.FAILURE)
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getRequestId()).isEqualTo(requestId.toString());
                assertThat(auditEvent.getRegion().getRegionId()).isEqualTo(fixture.regionId());
                assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT);
                assertThat(auditEvent.getPreviousState()).isEqualTo(ContentStatus.ENDED.name());
                assertThat(auditEvent.getNextState()).isNull();
                assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.FAILURE);
                assertThat(auditEvent.getReasonCode()).isEqualTo(ErrorCode.NOT_FOUND.name());
                assertThat(auditEvent.getActorKind()).isEqualTo("USER");
                assertThat(auditEvent.getActorRole()).isEqualTo(UserRole.OPERATOR.name());
                assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                    .hasValueSatisfying(link ->
                        assertThat(link.getActor().getUserId()).isEqualTo(fixture.operatorId())
                    );
            });
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent content operation did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent content operation was interrupted", exception);
        }
    }

    private record Fixture(Long adminId, Long operatorId, Long regionId, Long contentId) {
    }

    private record Attempt(ErrorCode errorCode) {
    }
}
