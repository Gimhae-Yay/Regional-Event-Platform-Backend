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
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.content.repository.SessionRevisionRepository;
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
class SessionRevisionApprovalUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final Instant ORIGINAL_STARTS_AT = Instant.parse("2099-08-29T01:00:00Z");
    private static final Instant ORIGINAL_ENDS_AT = Instant.parse("2099-08-29T03:00:00Z");
    private static final Instant ORIGINAL_CHECKIN_OPEN_AT = Instant.parse("2099-08-29T00:30:00Z");
    private static final Instant ORIGINAL_CHECKIN_CLOSE_AT = Instant.parse("2099-08-29T02:30:00Z");
    private static final Instant CANDIDATE_STARTS_AT = Instant.parse("2099-08-30T01:00:00Z");
    private static final Instant CANDIDATE_ENDS_AT = Instant.parse("2099-08-30T03:00:00Z");
    private static final Instant CANDIDATE_CHECKIN_OPEN_AT = Instant.parse("2099-08-30T00:30:00Z");
    private static final Instant CANDIDATE_CHECKIN_CLOSE_AT = Instant.parse("2099-08-30T02:30:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-01T00:00:00Z");

    private final ApproveSessionRevisionUseCase approveSessionRevisionUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final SessionRevisionRepository sessionRevisionRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    SessionRevisionApprovalUseCaseMySqlTest(
        ApproveSessionRevisionUseCase approveSessionRevisionUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        SessionRevisionRepository sessionRevisionRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.approveSessionRevisionUseCase = approveSessionRevisionUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.sessionRevisionRepository = sessionRevisionRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    @Timeout(15)
    void approve_whenRequestsRace_commitsOnlyOneTerminalTransition() throws Exception {
        Fixture fixture = createFixture();
        ConcurrentOutcomes outcomes = race(
            () -> approve(fixture),
            () -> approve(fixture)
        );

        assertThat(outcomes.values()).containsExactlyInAnyOrder("APPROVED", "CONFLICT");
        SessionRevision revision = sessionRevisionRepository.findById(fixture.revisionId()).orElseThrow();
        ContentSession contentSession = contentSessionRepository.findById(fixture.sessionId()).orElseThrow();
        assertThat(revision.getStatus()).isEqualTo(SessionRevisionStatus.APPROVED);
        assertThat(contentSession.getStartsAt()).isEqualTo(CANDIDATE_STARTS_AT);
        assertThat(contentSession.getVersionNo()).isEqualTo(fixture.baseSessionVersion() + 1);
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> fixture.sessionId().equals(auditEvent.getTargetId()))
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT_SESSION);
                assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                    .hasValueSatisfying(actorLink ->
                        assertThat(actorLink.getActor().getUserId()).isEqualTo(fixture.adminId())
                    );
            });
    }

    private ConcurrentOutcomes race(
        ConcurrentAction firstAction,
        ConcurrentAction secondAction
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<String> first = executorService.submit(() -> executeAfterStart(firstAction, ready, start));
            Future<String> second = executorService.submit(() -> executeAfterStart(secondAction, ready, start));
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return new ConcurrentOutcomes(List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            ));
        }
    }

    private String executeAfterStart(
        ConcurrentAction action,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        try {
            if (!start.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent approval did not start in time");
            }
            return action.execute();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent approval was interrupted", exception);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.SESSION_STATE_CONFLICT) {
                return "CONFLICT";
            }
            throw exception;
        }
    }

    private String approve(Fixture fixture) {
        approveSessionRevisionUseCase.approve(
            fixture.adminId(),
            fixture.revisionId(),
            UUID.randomUUID()
        );
        return "APPROVED";
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("REGION-" + suffix, "region", true));
            AppUser admin = appUserRepository.save(new AppUser(
                "admin-" + suffix + "@example.com",
                "hashed-password",
                "admin",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.save(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
            AppUser operator = appUserRepository.save(new AppUser(
                "operator-" + suffix + "@example.com",
                "hashed-password",
                "operator",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            Content content = contentRepository.save(new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.APPROVED,
                "Session revision " + suffix,
                "description",
                "location",
                "hours",
                "055-000-0000",
                "precautions",
                "age",
                "materials",
                "cancellation policy",
                ORIGINAL_STARTS_AT
            ));
            ContentSession contentSession = new ContentSession(
                content,
                region,
                ORIGINAL_STARTS_AT,
                ORIGINAL_ENDS_AT,
                ORIGINAL_CHECKIN_OPEN_AT,
                ORIGINAL_CHECKIN_CLOSE_AT,
                20
            );
            contentSession.approve(admin, SUBMITTED_AT.minusSeconds(60));
            contentSession = contentSessionRepository.save(contentSession);
            SessionRevision revision = sessionRevisionRepository.save(new SessionRevision(
                content,
                region,
                contentSession,
                contentSession.getVersionNo(),
                CANDIDATE_STARTS_AT,
                CANDIDATE_ENDS_AT,
                CANDIDATE_CHECKIN_OPEN_AT,
                CANDIDATE_CHECKIN_CLOSE_AT,
                30,
                SessionRevisionStatus.PENDING,
                operator,
                SUBMITTED_AT,
                null,
                null,
                null
            ));
            sessionRevisionRepository.flush();
            return new Fixture(
                admin.getUserId(),
                contentSession.getSessionId(),
                revision.getSessionRevisionId(),
                contentSession.getVersionNo()
            );
        });
    }

    @FunctionalInterface
    private interface ConcurrentAction {

        String execute();
    }

    private record ConcurrentOutcomes(List<String> values) {
    }

    private record Fixture(
        Long adminId,
        Long sessionId,
        Long revisionId,
        int baseSessionVersion
    ) {
    }
}
