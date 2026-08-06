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
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
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

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RejectContentSessionUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final String FIRST_REASON = "첫 번째 반려 사유";
    private static final String SECOND_REASON = "두 번째 반려 사유";

    private final RejectContentSessionUseCase rejectContentSessionUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    RejectContentSessionUseCaseMySqlTest(
        RejectContentSessionUseCase rejectContentSessionUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.rejectContentSessionUseCase = rejectContentSessionUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    @Timeout(10)
    void 동시_반려는_한_건만_성공하고_감사_처리자_연결을_한번만_기록한다() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Attempt> attempts;
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<Attempt> first = executorService.submit(
                () -> rejectAfterStart(fixture, FIRST_REASON, ready, start)
            );
            Future<Attempt> second = executorService.submit(
                () -> rejectAfterStart(fixture, SECOND_REASON, ready, start)
            );
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }

        assertThat(attempts).filteredOn(attempt -> attempt.result() != null)
            .extracting(Attempt::result)
            .singleElement()
            .satisfies(result -> assertThat(result.status()).isEqualTo(ContentSessionStatus.REJECTED));
        assertThat(attempts).filteredOn(attempt -> attempt.errorCode() != null)
            .extracting(Attempt::errorCode)
            .containsExactly(ErrorCode.SESSION_STATE_CONFLICT);
        assertThat(contentSessionRepository.findById(fixture.sessionId()))
            .hasValueSatisfying(session -> {
                assertThat(session.getStatus()).isEqualTo(ContentSessionStatus.REJECTED);
                assertThat(session.getRejectReason()).isIn(FIRST_REASON, SECOND_REASON);
                assertThat(session.getReviewedByUser().getUserId()).isEqualTo(fixture.adminId());
            });
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> fixture.sessionId().equals(auditEvent.getTargetId()))
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT_SESSION);
                assertThat(auditEvent.getPreviousState()).isEqualTo("PENDING");
                assertThat(auditEvent.getNextState()).isEqualTo("REJECTED");
                assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                    .hasValueSatisfying(actorLink ->
                        assertThat(actorLink.getActor().getUserId()).isEqualTo(fixture.adminId())
                    );
            });
    }

    private Attempt rejectAfterStart(
        Fixture fixture,
        String reason,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        try {
            return new Attempt(rejectContentSessionUseCase.reject(
                fixture.adminId(),
                fixture.sessionId(),
                reason,
                UUID.randomUUID()
            ), null);
        } catch (BusinessException exception) {
            return new Attempt(null, exception.getErrorCode());
        }
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("R" + suffix, "김해시", true));
            AppUser admin = appUserRepository.save(new AppUser(
                "admin-" + suffix + "@example.com",
                "hashed-password",
                "관리자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(
                admin,
                UserRole.REGION_ADMIN,
                region
            ));
            AppUser operator = appUserRepository.save(new AppUser(
                "operator-" + suffix + "@example.com",
                "hashed-password",
                "운영자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            Content content = contentRepository.save(new Content(
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
                Instant.parse("2026-08-05T00:00:00Z")
            ));
            Instant startsAt = Instant.parse("2030-08-10T01:00:00Z");
            ContentSession session = contentSessionRepository.save(new ContentSession(
                content,
                region,
                startsAt,
                startsAt.plusSeconds(7_200),
                startsAt.minusSeconds(1_800),
                startsAt.plusSeconds(5_400),
                20
            ));
            return new Fixture(admin.getUserId(), session.getSessionId());
        });
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent rejection did not start in time");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent rejection was interrupted", exception);
        }
    }

    private record Attempt(
        RejectContentSessionResult result,
        ErrorCode errorCode
    ) {
    }

    private record Fixture(
        Long adminId,
        Long sessionId
    ) {
    }
}
