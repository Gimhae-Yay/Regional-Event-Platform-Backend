package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
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

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RejectSessionRevisionUseCaseMySqlTest {

    private static final Instant STARTS_AT = Instant.parse("2026-08-20T01:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-08-20T03:00:00Z");
    private static final Instant CHECKIN_OPEN_AT = Instant.parse("2026-08-20T00:30:00Z");
    private static final Instant CHECKIN_CLOSE_AT = Instant.parse("2026-08-20T02:30:00Z");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42");

    private final RejectSessionRevisionUseCase rejectSessionRevisionUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final SessionRevisionRepository sessionRevisionRepository;
    private final AuditEventRepository auditEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    RejectSessionRevisionUseCaseMySqlTest(
        RejectSessionRevisionUseCase rejectSessionRevisionUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        SessionRevisionRepository sessionRevisionRepository,
        AuditEventRepository auditEventRepository,
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.rejectSessionRevisionUseCase = rejectSessionRevisionUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.sessionRevisionRepository = sessionRevisionRepository;
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
    @Timeout(15)
    void MySQL에서_수정_요청만_반려하고_감사_처리자_연결을_기록한다() {
        Fixture fixture = createFixture();

        RejectSessionRevisionResult result = rejectSessionRevisionUseCase.reject(
            fixture.adminId(),
            fixture.revisionId(),
            "  정원 변경 사유를 보완해 주세요.  ",
            UUID.randomUUID()
        );

        assertThat(result.revisionStatus()).isEqualTo(SessionRevisionStatus.REJECTED);
        assertThat(result.rejectReason()).isEqualTo("정원 변경 사유를 보완해 주세요.");
        SessionRevision revision = sessionRevisionRepository.findById(fixture.revisionId()).orElseThrow();
        ContentSession session = contentSessionRepository.findById(fixture.sessionId()).orElseThrow();
        assertThat(revision.getStatus()).isEqualTo(SessionRevisionStatus.REJECTED);
        assertThat(revision.getReviewedAt()).isNotNull();
        assertThat(revision.getReviewedBy().getUserId()).isEqualTo(fixture.adminId());
        assertThat(revision.getRejectReason()).isEqualTo("정원 변경 사유를 보완해 주세요.");
        assertThat(session.getStatus()).isEqualTo(fixture.sessionStatus());
        assertThat(session.getStartsAt()).isEqualTo(STARTS_AT);
        assertThat(session.getVersionNo()).isEqualTo(fixture.sessionVersion());

        List<AuditEvent> auditEvents = auditEventRepository.findAll().stream()
            .filter(auditEvent -> fixture.sessionId().equals(auditEvent.getTargetId()))
            .toList();
        assertThat(auditEvents).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT_SESSION);
            assertThat(auditEvent.getPreviousState()).isEqualTo(SessionRevisionStatus.PENDING.name());
            assertThat(auditEvent.getNextState()).isEqualTo(SessionRevisionStatus.REJECTED.name());
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
        });
        assertThat(jdbcTemplate.queryForList(
            "SELECT user_id FROM audit_event_actor_link WHERE audit_event_id = ?",
            Long.class,
            auditEvents.getFirst().getAuditEventId()
        )).containsExactly(fixture.adminId());

        assertThatThrownBy(() -> rejectSessionRevisionUseCase.reject(
            fixture.adminId(),
            fixture.revisionId(),
            "다시 반려",
            UUID.randomUUID()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SESSION_STATE_CONFLICT)
        );
        assertThat(auditEventRepository.findAll()).hasSize(1);
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region region = regionRepository.save(new Region("R" + suffix, "김해시", true));
            AppUser admin = saveUser("admin-" + suffix);
            userRoleAssignmentRepository.save(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
            AppUser operator = saveUser("operator-" + suffix);
            Content content = contentRepository.save(new Content(
                region,
                operator,
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.PUBLISHED,
                "원본 제목",
                "원본 설명",
                "원본 장소",
                "원본 운영 시간",
                "055-1234-5678",
                "원본 주의사항",
                "만 7세 이상",
                "편한 복장",
                "원본 취소 정책",
                Instant.parse("2026-08-01T00:00:00Z")
            ));
            ContentSession session = new ContentSession(
                content,
                region,
                STARTS_AT,
                ENDS_AT,
                CHECKIN_OPEN_AT,
                CHECKIN_CLOSE_AT,
                20
            );
            session.approve(admin, Instant.parse("2026-08-01T01:00:00Z"));
            session = contentSessionRepository.save(session);
            SessionRevision revision = sessionRevisionRepository.save(new SessionRevision(
                content,
                region,
                session,
                session.getVersionNo(),
                STARTS_AT.plusSeconds(86_400),
                ENDS_AT.plusSeconds(86_400),
                CHECKIN_OPEN_AT.plusSeconds(86_400),
                CHECKIN_CLOSE_AT.plusSeconds(86_400),
                30,
                SessionRevisionStatus.PENDING,
                operator,
                Instant.parse("2026-08-02T00:00:00Z"),
                null,
                null,
                null
            ));
            return new Fixture(
                admin.getUserId(),
                revision.getSessionRevisionId(),
                session.getSessionId(),
                session.getStatus(),
                session.getVersionNo()
            );
        });
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

    private record Fixture(
        Long adminId,
        Long revisionId,
        Long sessionId,
        ContentSessionStatus sessionStatus,
        int sessionVersion
    ) {
    }
}
