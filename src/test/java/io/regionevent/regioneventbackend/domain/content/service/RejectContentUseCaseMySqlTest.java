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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
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

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RejectContentUseCaseMySqlTest extends NonTransactionalMySqlTestSupport {

    private static final String FIRST_REASON = "첫 번째 반려 사유";
    private static final String SECOND_REASON = "두 번째 반려 사유";

    private final RejectContentUseCase rejectContentUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentLogRepository contentLogRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    RejectContentUseCaseMySqlTest(
        RejectContentUseCase rejectContentUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentLogRepository contentLogRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.rejectContentUseCase = rejectContentUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentLogRepository = contentLogRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    @Timeout(10)
    void 다른_사유의_동시_반려는_한_건만_성공하고_나머지는_충돌한다() throws Exception {
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
            attempts = List.of(
                first.get(5, TimeUnit.SECONDS),
                second.get(5, TimeUnit.SECONDS)
            );
        }

        assertThat(attempts).filteredOn(attempt -> attempt.result() != null)
            .extracting(Attempt::result)
            .singleElement()
            .satisfies(result -> assertThat(result.status()).isEqualTo(ContentStatus.REJECTED));
        assertThat(attempts).filteredOn(attempt -> attempt.errorCode() != null)
            .extracting(Attempt::errorCode)
            .containsExactly(ErrorCode.CONTENT_STATE_CONFLICT);
        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content ->
                assertThat(content.getStatus()).isEqualTo(ContentStatus.REJECTED)
            );
        List<ContentLog> logs = contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(
            fixture.contentId()
        );
        assertThat(logs).extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PENDING, ContentLogStatus.REJECTED);
        assertThat(logs.get(1).getReason()).isIn(FIRST_REASON, SECOND_REASON);
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()))
            .hasSize(1);
        assertThat(auditEventActorLinkRepository.count()).isEqualTo(1);
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
            return new Attempt(rejectContentUseCase.reject(
                fixture.adminId(),
                fixture.contentId(),
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
                ContentStatus.PENDING,
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
            contentLogRepository.save(new ContentLog(
                content,
                operator,
                ContentLogStatus.PENDING,
                null,
                Instant.parse("2026-08-01T00:00:00Z")
            ));
            return new Fixture(admin.getUserId(), content.getContentId());
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
        RejectContentResult result,
        ErrorCode errorCode
    ) {
    }

    private record Fixture(
        Long adminId,
        Long contentId
    ) {
    }
}
