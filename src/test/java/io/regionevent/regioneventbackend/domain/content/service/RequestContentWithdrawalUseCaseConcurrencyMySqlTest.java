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
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentWithdrawalRequestRepository;
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

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class RequestContentWithdrawalUseCaseConcurrencyMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final String FIRST_KEY = "request-key-1";
    private static final String SECOND_KEY = "request-key-2";
    private static final String REQUEST_REASON = "운영 계획 변경";

    private final RequestContentWithdrawalUseCase requestUseCase;
    private final RejectContentWithdrawalUseCase rejectUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository roleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentWithdrawalRequestRepository withdrawalRequestRepository;
    private final AuditEventRepository auditEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    RequestContentWithdrawalUseCaseConcurrencyMySqlTest(
        RequestContentWithdrawalUseCase requestUseCase,
        RejectContentWithdrawalUseCase rejectUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository roleAssignmentRepository,
        ContentRepository contentRepository,
        ContentWithdrawalRequestRepository withdrawalRequestRepository,
        AuditEventRepository auditEventRepository,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.requestUseCase = requestUseCase;
        this.rejectUseCase = rejectUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.auditEventRepository = auditEventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    @Timeout(15)
    void 같은_키와_같은_사유의_동시_요청은_같은_최초_결과로_수렴한다() throws Exception {
        Fixture fixture = createFixture();

        List<RequestAttempt> attempts = requestConcurrently(
            fixture,
            FIRST_KEY,
            REQUEST_REASON,
            FIRST_KEY,
            REQUEST_REASON
        );

        assertDifferentConnections(attempts);
        assertThat(attempts).allMatch(RequestAttempt::isSuccessful);
        assertThat(attempts)
            .extracting(attempt -> attempt.result().withdrawalRequestId())
            .containsOnly(attempts.getFirst().result().withdrawalRequestId());
        assertSingleRequestAndAudit();
    }

    @Test
    @Timeout(15)
    void 같은_키와_다른_사유의_동시_요청은_최초_사유만_보존한다() throws Exception {
        Fixture fixture = createFixture();

        List<RequestAttempt> attempts = requestConcurrently(
            fixture,
            FIRST_KEY,
            "첫 사유",
            FIRST_KEY,
            "두 번째 사유"
        );

        assertDifferentConnections(attempts);
        assertThat(attempts).filteredOn(RequestAttempt::isSuccessful).singleElement();
        assertThat(attempts)
            .filteredOn(attempt -> !attempt.isSuccessful())
            .extracting(RequestAttempt::errorCode)
            .containsExactly(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        assertThat(withdrawalRequestRepository.findAll()).singleElement()
            .satisfies(request -> assertThat(request.getRequestReason()).isIn("첫 사유", "두 번째 사유"));
        assertSingleRequestAndAudit();
    }

    @Test
    @Timeout(15)
    void 다른_키의_동시_요청은_하나만_PENDING으로_생성한다() throws Exception {
        Fixture fixture = createFixture();

        List<RequestAttempt> attempts = requestConcurrently(
            fixture,
            FIRST_KEY,
            REQUEST_REASON,
            SECOND_KEY,
            REQUEST_REASON
        );

        assertDifferentConnections(attempts);
        assertThat(attempts).filteredOn(RequestAttempt::isSuccessful).singleElement();
        assertThat(attempts)
            .filteredOn(attempt -> !attempt.isSuccessful())
            .extracting(RequestAttempt::errorCode)
            .containsExactly(ErrorCode.CONTENT_STATE_CONFLICT);
        assertSingleRequestAndAudit();
    }

    @Test
    @Timeout(15)
    void 반려_뒤_같은_키는_최초_결과를_재사용하고_새_키만_새_요청을_만든다() throws Exception {
        Fixture fixture = createFixture();
        RequestContentWithdrawalResult first = request(
            fixture,
            FIRST_KEY,
            REQUEST_REASON
        );
        rejectUseCase.reject(
            fixture.adminId(),
            first.withdrawalRequestId(),
            "근거 보완 필요",
            UUID.randomUUID()
        );

        List<RequestAttempt> attempts = requestConcurrently(
            fixture,
            FIRST_KEY,
            REQUEST_REASON,
            SECOND_KEY,
            REQUEST_REASON
        );

        assertDifferentConnections(attempts);
        assertThat(attempts).allMatch(RequestAttempt::isSuccessful);
        assertThat(attempts)
            .filteredOn(attempt -> attempt.result().withdrawalRequestId().equals(first.withdrawalRequestId()))
            .singleElement()
            .satisfies(attempt -> assertThat(attempt.result().status())
                .isEqualTo(ContentWithdrawalRequestStatus.PENDING));
        assertThat(withdrawalRequestRepository.findAll())
            .extracting(request -> request.getStatus())
            .containsExactlyInAnyOrder(
                ContentWithdrawalRequestStatus.REJECTED,
                ContentWithdrawalRequestStatus.PENDING
            );
        assertThat(countWithdrawalRequestAudits()).isEqualTo(3);
    }

    private List<RequestAttempt> requestConcurrently(
        Fixture fixture,
        String firstKey,
        String firstReason,
        String secondKey,
        String secondReason
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<RequestAttempt> first = executorService.submit(
                () -> attemptRequest(fixture, firstKey, firstReason, ready, start)
            );
            Future<RequestAttempt> second = executorService.submit(
                () -> attemptRequest(fixture, secondKey, secondReason, ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            );
        }
    }

    private RequestAttempt attemptRequest(
        Fixture fixture,
        String key,
        String reason,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        AtomicLong connectionId = new AtomicLong();
        try {
            RequestContentWithdrawalResult result = transactionTemplate.execute(status -> {
                connectionId.set(findCurrentConnectionId());
                ready.countDown();
                await(start);
                return request(fixture, key, reason);
            });
            return new RequestAttempt(result, null, connectionId.get());
        } catch (BusinessException exception) {
            return new RequestAttempt(null, exception.getErrorCode(), connectionId.get());
        }
    }

    private RequestContentWithdrawalResult request(Fixture fixture, String key, String reason) {
        return requestUseCase.request(
            fixture.operatorId(),
            fixture.contentId(),
            key,
            reason,
            UUID.randomUUID()
        );
    }

    private void assertDifferentConnections(List<RequestAttempt> attempts) {
        assertThat(attempts).extracting(RequestAttempt::connectionId).doesNotHaveDuplicates();
    }

    private void assertSingleRequestAndAudit() {
        assertThat(withdrawalRequestRepository.findAll()).singleElement()
            .satisfies(request -> assertThat(request.getStatus())
                .isEqualTo(ContentWithdrawalRequestStatus.PENDING));
        assertThat(countWithdrawalRequestAudits()).isOne();
    }

    private long countWithdrawalRequestAudits() {
        return auditEventRepository.findAll().stream()
            .filter(event -> event.getTargetType() == AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST)
            .count();
    }

    private long findCurrentConnectionId() {
        Long connectionId = jdbcTemplate.queryForObject("SELECT CONNECTION_ID()", Long.class);
        if (connectionId == null) {
            throw new IllegalStateException("MySQL connection id does not exist");
        }
        return connectionId;
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant now = Instant.now();
            Region region = regionRepository.saveAndFlush(new Region("REQUEST-" + suffix, "김해시", true));
            AppUser admin = saveUser("admin-" + suffix);
            roleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
                admin,
                UserRole.REGION_ADMIN,
                region
            ));
            AppUser operator = saveUser("operator-" + suffix);
            roleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
                operator,
                UserRole.OPERATOR,
                region
            ));
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
                now.minusSeconds(86_400)
            ));
            return new Fixture(admin.getUserId(), operator.getUserId(), content.getContentId());
        });
    }

    private AppUser saveUser(String prefix) {
        return appUserRepository.saveAndFlush(new AppUser(
            prefix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrency test synchronization timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrency test synchronization interrupted", exception);
        }
    }

    private record Fixture(Long adminId, Long operatorId, Long contentId) {
    }

    private record RequestAttempt(
        RequestContentWithdrawalResult result,
        ErrorCode errorCode,
        long connectionId
    ) {

        private boolean isSuccessful() {
            return result != null;
        }
    }
}
