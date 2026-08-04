package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
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
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Import(SuspendContentFailureAuditMySqlTest.FailingSuspensionServicesConfig.class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SuspendContentFailureAuditMySqlTest extends NonTransactionalMySqlTestSupport {

    private final SuspendContentUseCase suspendContentUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentLogRepository contentLogRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final JdbcTemplate jdbcTemplate;
    private final FailingContentService failingContentService;
    private final FailingContentLogService failingContentLogService;
    private final FailingCapacityHoldService failingCapacityHoldService;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    SuspendContentFailureAuditMySqlTest(
        SuspendContentUseCase suspendContentUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        ContentLogRepository contentLogRepository,
        CapacityHoldRepository capacityHoldRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        JdbcTemplate jdbcTemplate,
        FailingContentService failingContentService,
        FailingContentLogService failingContentLogService,
        FailingCapacityHoldService failingCapacityHoldService
    ) {
        this.suspendContentUseCase = suspendContentUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.contentLogRepository = contentLogRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.failingContentService = failingContentService;
        this.failingContentLogService = failingContentLogService;
        this.failingCapacityHoldService = failingCapacityHoldService;
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @AfterEach
    void resetFailureInjection() {
        failingContentService.resetFailureInjection();
        failingContentLogService.resetFailureInjection();
        failingCapacityHoldService.resetFailureInjection();
    }

    @Test
    void 콘텐츠_전이_직후_실패하면_업무변경을_롤백하고_비개인_실패감사를_기록한다() {
        Fixture fixture = createFixture();
        failingContentService.failNextSuspend();

        assertAtomicRollback(fixture, "content transition failure");
    }

    @Test
    void 중단로그_저장_직후_실패하면_업무변경을_롤백하고_비개인_실패감사를_기록한다() {
        Fixture fixture = createFixture();
        failingContentLogService.failNextSuspendedLog();

        assertAtomicRollback(fixture, "content log storage failure");
    }

    @Test
    void 홀드_무효화와_정원복구_직후_실패하면_업무변경을_롤백하고_비개인_실패감사를_기록한다() {
        Fixture fixture = createFixture();
        failingCapacityHoldService.failNextInvalidation();

        assertAtomicRollback(fixture, "capacity hold invalidation failure");
    }

    @Test
    void 성공감사_기록에_실패하면_중단변경을_롤백하고_비개인_실패감사를_기록한다() {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        assertAtomicRollback(fixture, "audit storage failure");
    }

    private void assertAtomicRollback(Fixture fixture, String failureMessage) {
        assertThatThrownBy(() -> suspendContentUseCase.suspend(
            fixture.adminId(),
            fixture.contentId(),
            "기상 악화",
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage(failureMessage);

        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED));
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED);
        assertThat(capacityHoldRepository.findById(fixture.holdId()))
            .hasValueSatisfying(hold -> {
                assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE);
                assertThat(hold.getTerminalAt()).isNull();
                assertThat(hold.getCapacityReleasedAt()).isNull();
            });
        assertThat(contentSessionRepository.findById(fixture.sessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(8));
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.FAILURE);
            assertThat(auditEvent.getReasonCode()).isEqualTo("INTERNAL_SERVER_ERROR");
            assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId())).isEmpty();
        });
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser admin = saveUser("admin-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
        AppUser operator = saveUser("operator-" + suffix);
        AppUser visitor = saveUser("visitor-" + suffix);
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
        contentLogRepository.saveAndFlush(new ContentLog(
            content,
            operator,
            ContentLogStatus.PUBLISHED,
            null,
            now.minusSeconds(86_400)
        ));
        ContentSession session = new ContentSession(
            content,
            region,
            now.plusSeconds(3_600),
            now.plusSeconds(14_400),
            now.plusSeconds(1_800),
            now.plusSeconds(12_600),
            10
        );
        session.approve(admin, now);
        session = contentSessionRepository.saveAndFlush(session);
        jdbcTemplate.update(
            "UPDATE content_session SET remaining_capacity = 8 WHERE session_id = ?",
            session.getSessionId()
        );
        CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            visitor,
            2,
            CapacityHoldStatus.ACTIVE,
            now.plusSeconds(600),
            null,
            null,
            null,
            now
        ));
        return new Fixture(admin.getUserId(), content.getContentId(), session.getSessionId(), hold.getHoldId());
    }

    private AppUser saveUser(String identifierPrefix) {
        return appUserRepository.saveAndFlush(new AppUser(
            identifierPrefix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingSuspensionServicesConfig {

        @Bean
        @Primary
        FailingContentService failingContentService(ContentRepository contentRepository) {
            return new FailingContentService(contentRepository);
        }

        @Bean
        @Primary
        FailingContentLogService failingContentLogService(ContentLogRepository contentLogRepository) {
            return new FailingContentLogService(contentLogRepository);
        }

        @Bean
        @Primary
        FailingCapacityHoldService failingCapacityHoldService(CapacityHoldRepository capacityHoldRepository) {
            return new FailingCapacityHoldService(capacityHoldRepository);
        }
    }

    static class FailingContentService extends ContentService {

        private final AtomicBoolean failNextSuspend = new AtomicBoolean();

        FailingContentService(ContentRepository contentRepository) {
            super(contentRepository);
        }

        @Override
        public Content suspend(Content content, Instant suspendedAt) {
            Content suspendedContent = super.suspend(content, suspendedAt);
            if (failNextSuspend.compareAndSet(true, false)) {
                throw new IllegalStateException("content transition failure");
            }
            return suspendedContent;
        }

        void failNextSuspend() {
            failNextSuspend.set(true);
        }

        void resetFailureInjection() {
            failNextSuspend.set(false);
        }
    }

    static class FailingContentLogService extends ContentLogService {

        private final AtomicBoolean failNextSuspendedLog = new AtomicBoolean();

        FailingContentLogService(ContentLogRepository contentLogRepository) {
            super(contentLogRepository);
        }

        @Override
        public ContentLog recordSuspended(
            Content content,
            AppUser actor,
            Instant suspendedAt,
            String reason
        ) {
            ContentLog contentLog = super.recordSuspended(content, actor, suspendedAt, reason);
            if (failNextSuspendedLog.compareAndSet(true, false)) {
                throw new IllegalStateException("content log storage failure");
            }
            return contentLog;
        }

        void failNextSuspendedLog() {
            failNextSuspendedLog.set(true);
        }

        void resetFailureInjection() {
            failNextSuspendedLog.set(false);
        }
    }

    static class FailingCapacityHoldService extends CapacityHoldService {

        private final AtomicBoolean failNextInvalidation = new AtomicBoolean();

        FailingCapacityHoldService(CapacityHoldRepository capacityHoldRepository) {
            super(capacityHoldRepository);
        }

        @Override
        @Transactional(propagation = Propagation.MANDATORY)
        public void invalidateAllActiveHoldsForContent(
            Long contentId,
            String invalidationReason
        ) {
            super.invalidateAllActiveHoldsForContent(contentId, invalidationReason);
            if (failNextInvalidation.compareAndSet(true, false)) {
                throw new IllegalStateException("capacity hold invalidation failure");
            }
        }

        void failNextInvalidation() {
            failNextInvalidation.set(true);
        }

        void resetFailureInjection() {
            failNextInvalidation.set(false);
        }
    }

    private record Fixture(Long adminId, Long contentId, Long sessionId, Long holdId) {
    }
}
