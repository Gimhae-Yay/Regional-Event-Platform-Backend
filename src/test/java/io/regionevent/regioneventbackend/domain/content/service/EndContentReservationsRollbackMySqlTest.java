package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

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
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.support.mysql.DelayedSchedulersMySqlTestSupport;

@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EndContentReservationsRollbackMySqlTest extends DelayedSchedulersMySqlTestSupport {

    private static final int SESSION_CAPACITY = 10;

    private final EndContentReservationsUseCase endContentReservationsUseCase;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final ContentLogRepository contentLogRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final AuditEventRepository auditEventRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    EndContentReservationsRollbackMySqlTest(
        EndContentReservationsUseCase endContentReservationsUseCase,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        ContentLogRepository contentLogRepository,
        CapacityHoldRepository capacityHoldRepository,
        AuditEventRepository auditEventRepository,
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
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.jdbcTemplate = jdbcTemplate;
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void 감사_기록이_실패하면_콘텐츠와_홀드와_정원이_함께_롤백된다() {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> endContentReservationsUseCase.end(
            fixture.adminId(),
            fixture.contentId(),
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage("audit storage failure");

        assertThat(contentRepository.findById(fixture.contentId()))
            .hasValueSatisfying(content -> assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED));
        assertThat(contentLogRepository.findByContentContentIdOrderByDateAscIdAsc(fixture.contentId()))
            .extracting(ContentLog::getStatus)
            .containsExactly(ContentLogStatus.PUBLISHED);
        assertThat(capacityHoldRepository.findById(fixture.firstHoldId()))
            .hasValueSatisfying(this::assertActive);
        assertThat(capacityHoldRepository.findById(fixture.secondHoldId()))
            .hasValueSatisfying(this::assertActive);
        assertThat(contentSessionRepository.findById(fixture.firstSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(8));
        assertThat(contentSessionRepository.findById(fixture.secondSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(9));
        assertThat(auditEventRepository.findAll())
            .noneMatch(auditEvent -> fixture.contentId().equals(auditEvent.getTargetId()));
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Instant now = Instant.now();
            Region region = regionRepository.save(new Region("R" + suffix, "김해시", true));
            AppUser admin = saveUser("admin-" + suffix);
            userRoleAssignmentRepository.save(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
            AppUser operator = saveUser("operator-" + suffix);
            AppUser visitor = saveUser("visitor-" + suffix);
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
                "안내를 따라주세요.",
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
            ContentSession secondSession = saveCompletedSession(content, region, admin, now.plusSeconds(10_800));
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
        ContentSession session = new ContentSession(
            content,
            region,
            startsAt,
            startsAt.plusSeconds(10_800),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(9_000),
            SESSION_CAPACITY
        );
        session.approve(admin, startsAt.minusSeconds(3_600));
        session.complete(startsAt.minusSeconds(1_800));
        return contentSessionRepository.save(session);
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

    private AppUser saveUser(String identifierPrefix) {
        return appUserRepository.save(new AppUser(
            identifierPrefix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private void assertActive(CapacityHold capacityHold) {
        assertThat(capacityHold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE);
        assertThat(capacityHold.getInvalidationReason()).isNull();
        assertThat(capacityHold.getTerminalAt()).isNull();
        assertThat(capacityHold.getCapacityReleasedAt()).isNull();
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
