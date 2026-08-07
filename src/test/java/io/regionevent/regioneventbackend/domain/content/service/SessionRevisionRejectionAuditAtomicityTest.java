package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentSessionRequest;
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
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;

@DataJpaTest
@Import({
    RejectSessionRevisionUseCase.class,
    CreateSessionRevisionUseCase.class,
    ContentService.class,
    ContentSessionService.class,
    SessionRevisionService.class,
    OperatorAuthorizationService.class,
    RegionAdminAuthorizationService.class,
    SessionRevisionRejectionAuditAtomicityTest.ClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SessionRevisionRejectionAuditAtomicityTest {

    private static final Instant STARTS_AT = Instant.parse("2026-08-20T01:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-08-20T03:00:00Z");
    private static final Instant CHECKIN_OPEN_AT = Instant.parse("2026-08-20T00:30:00Z");
    private static final Instant CHECKIN_CLOSE_AT = Instant.parse("2026-08-20T02:30:00Z");

    private final RejectSessionRevisionUseCase rejectSessionRevisionUseCase;
    private final CreateSessionRevisionUseCase createSessionRevisionUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final SessionRevisionRepository sessionRevisionRepository;
    private final AuditEventRepository auditEventRepository;
    private final EntityManager entityManager;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    SessionRevisionRejectionAuditAtomicityTest(
        RejectSessionRevisionUseCase rejectSessionRevisionUseCase,
        CreateSessionRevisionUseCase createSessionRevisionUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        SessionRevisionRepository sessionRevisionRepository,
        AuditEventRepository auditEventRepository,
        EntityManager entityManager
    ) {
        this.rejectSessionRevisionUseCase = rejectSessionRevisionUseCase;
        this.createSessionRevisionUseCase = createSessionRevisionUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.sessionRevisionRepository = sessionRevisionRepository;
        this.auditEventRepository = auditEventRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 감사_기록에_실패하면_수정_요청과_대상_회차를_변경하지_않는다() {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> rejectSessionRevisionUseCase.reject(
            fixture.adminId(),
            fixture.revisionId(),
            "반려 사유",
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class);

        entityManager.clear();
        SessionRevision unchangedRevision = sessionRevisionRepository.findById(fixture.revisionId()).orElseThrow();
        ContentSession unchangedSession = contentSessionRepository.findById(fixture.sessionId()).orElseThrow();
        assertThat(unchangedRevision.getStatus()).isEqualTo(SessionRevisionStatus.PENDING);
        assertThat(unchangedRevision.getReviewedAt()).isNull();
        assertThat(unchangedRevision.getReviewedBy()).isNull();
        assertThat(unchangedRevision.getRejectReason()).isNull();
        assertThat(unchangedSession.getStatus()).isEqualTo(fixture.sessionStatus());
        assertThat(unchangedSession.getStartsAt()).isEqualTo(STARTS_AT);
        assertThat(unchangedSession.getVersionNo()).isEqualTo(fixture.sessionVersion());
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void 감사_기록에_실패하면_회차_수정_요청을_저장하지_않는다() {
        Fixture fixture = createFixture();
        Content content = contentRepository.findById(fixture.contentId()).orElseThrow();
        AppUser admin = appUserRepository.findById(fixture.adminId()).orElseThrow();
        ContentSession targetSession = new ContentSession(
            content,
            content.getRegion(),
            STARTS_AT.plusSeconds(604_800),
            ENDS_AT.plusSeconds(604_800),
            CHECKIN_OPEN_AT.plusSeconds(604_800),
            CHECKIN_CLOSE_AT.plusSeconds(604_800),
            20
        );
        targetSession.approve(admin, Instant.parse("2026-08-01T01:00:00Z"));
        targetSession = contentSessionRepository.saveAndFlush(targetSession);
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        ContentSession finalTargetSession = targetSession;
        assertThatThrownBy(() -> createSessionRevisionUseCase.create(
            fixture.operatorId(),
            finalTargetSession.getSessionId(),
            new CreateContentSessionRequest(
                OffsetDateTime.parse("2026-08-27T10:00:00+09:00"),
                OffsetDateTime.parse("2026-08-27T12:00:00+09:00"),
                OffsetDateTime.parse("2026-08-27T09:30:00+09:00"),
                OffsetDateTime.parse("2026-08-27T11:30:00+09:00"),
                30
            ),
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class);

        entityManager.clear();
        Number pendingRevisionCount = (Number) entityManager.createNativeQuery("""
            SELECT COUNT(*)
            FROM session_revision
            WHERE target_session_id = :sessionId
                AND status = 'PENDING'
            """).setParameter("sessionId", finalTargetSession.getSessionId()).getSingleResult();
        assertThat(pendingRevisionCount.longValue()).isZero();
        assertThat(auditEventRepository.count()).isZero();
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser admin = saveUser("admin-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
        AppUser operator = saveUser("operator-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        Content content = contentRepository.saveAndFlush(new Content(
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
        session = contentSessionRepository.saveAndFlush(session);
        SessionRevision revision = sessionRevisionRepository.saveAndFlush(new SessionRevision(
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
            operator.getUserId(),
            content.getContentId(),
            revision.getSessionRevisionId(),
            session.getSessionId(),
            session.getStatus(),
            session.getVersionNo()
        );
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

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-05T01:00:00Z"), ZoneOffset.UTC);
        }
    }

    private record Fixture(
        Long adminId,
        Long operatorId,
        Long contentId,
        Long revisionId,
        Long sessionId,
        ContentSessionStatus sessionStatus,
        int sessionVersion
    ) {
    }
}
