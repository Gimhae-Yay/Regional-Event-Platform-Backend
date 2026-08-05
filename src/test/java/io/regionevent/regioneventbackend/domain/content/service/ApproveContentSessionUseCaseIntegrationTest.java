package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
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
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;
import io.regionevent.regioneventbackend.support.jpa.ContentAtomicityJpaTestConfiguration;

@DataJpaTest
@Import(ContentAtomicityJpaTestConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class ApproveContentSessionUseCaseIntegrationTest {

    private final ApproveContentSessionUseCase approveContentSessionUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;

    @Autowired
    ApproveContentSessionUseCaseIntegrationTest(
        ApproveContentSessionUseCase approveContentSessionUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository
    ) {
        this.approveContentSessionUseCase = approveContentSessionUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
    }

    @ParameterizedTest
    @EnumSource(value = ContentStatus.class, names = {"APPROVED", "PUBLISHED"})
    void 승인_가능한_콘텐츠의_추가_회차를_승인하면_감사와_처리자_연결을_기록하고_홀드와_예약은_만들지_않는다(
        ContentStatus contentStatus
    ) {
        Fixture fixture = createFixture(contentStatus, ContentSessionStatus.PENDING);
        long holdCount = capacityHoldRepository.count();
        long reservationCount = reservationRepository.count();

        ApproveContentSessionResult result = approveContentSessionUseCase.approve(
            fixture.admin().getUserId(),
            fixture.session().getSessionId(),
            UUID.randomUUID()
        );

        assertThat(result.status()).isEqualTo(ContentSessionStatus.SCHEDULED);
        assertThat(result.reviewedAt()).isNotNull();
        ContentSession approvedSession = contentSessionRepository.findById(fixture.session().getSessionId())
            .orElseThrow();
        assertThat(approvedSession.getStatus()).isEqualTo(ContentSessionStatus.SCHEDULED);
        assertThat(approvedSession.getReviewedAt()).isEqualTo(result.reviewedAt());
        AuditEvent auditEvent = auditEventRepository.findAll().getFirst();
        assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT_SESSION);
        assertThat(auditEvent.getTargetId()).isEqualTo(fixture.session().getSessionId());
        assertThat(auditEvent.getPreviousState()).isEqualTo(ContentSessionStatus.PENDING.name());
        assertThat(auditEvent.getNextState()).isEqualTo(ContentSessionStatus.SCHEDULED.name());
        assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(auditEventActorLinkRepository.count()).isOne();
        assertThat(capacityHoldRepository.count()).isEqualTo(holdCount);
        assertThat(reservationRepository.count()).isEqualTo(reservationCount);
    }

    @Test
    void 콘텐츠나_회차가_승인_조건과_다르면_상태_충돌이고_감사를_기록하지_않는다() {
        Fixture contentPending = createFixture(ContentStatus.PENDING, ContentSessionStatus.PENDING);
        Fixture sessionScheduled = createFixture(ContentStatus.PUBLISHED, ContentSessionStatus.SCHEDULED);

        for (Fixture fixture : new Fixture[] {contentPending, sessionScheduled}) {
            assertThatThrownBy(() -> approveContentSessionUseCase.approve(
                fixture.admin().getUserId(),
                fixture.session().getSessionId(),
                UUID.randomUUID()
            )).isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.SESSION_STATE_CONFLICT));
        }

        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void 담당_지역이_다르거나_콘텐츠가_삭제되면_승인하지_않는다() {
        Fixture fixture = createFixture(ContentStatus.APPROVED, ContentSessionStatus.PENDING);
        Region otherRegion = regionRepository.saveAndFlush(new Region("OTHER", "다른 지역", true));
        AppUser otherAdmin = saveUser("other-admin");
        userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(otherAdmin, UserRole.REGION_ADMIN, otherRegion)
        );

        assertThatThrownBy(() -> approveContentSessionUseCase.approve(
            otherAdmin.getUserId(),
            fixture.session().getSessionId(),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
            .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN));

        fixture.content().softDelete(Instant.parse("2026-08-04T00:00:00Z"));
        contentRepository.saveAndFlush(fixture.content());
        assertThatThrownBy(() -> approveContentSessionUseCase.approve(
            fixture.admin().getUserId(),
            fixture.session().getSessionId(),
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
            .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND));

        assertThat(contentSessionRepository.findById(fixture.session().getSessionId()).orElseThrow().getStatus())
            .isEqualTo(ContentSessionStatus.PENDING);
        assertThat(auditEventRepository.count()).isZero();
    }

    private Fixture createFixture(
        ContentStatus contentStatus,
        ContentSessionStatus sessionStatus
    ) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser admin = saveUser("admin-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
        AppUser operator = saveUser("operator-" + suffix);
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            contentStatus,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            Instant.parse("2026-08-05T00:00:00Z")
        ));
        ContentSession session = new ContentSession(
            content,
            region,
            Instant.parse("2026-08-10T01:00:00Z"),
            Instant.parse("2026-08-10T03:00:00Z"),
            Instant.parse("2026-08-10T00:30:00Z"),
            Instant.parse("2026-08-10T02:30:00Z"),
            20
        );
        if (sessionStatus == ContentSessionStatus.SCHEDULED) {
            session.approve(admin, Instant.parse("2026-08-01T00:00:00Z"));
        }
        return new Fixture(admin, content, contentSessionRepository.saveAndFlush(session));
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

    private record Fixture(
        AppUser admin,
        Content content,
        ContentSession session
    ) {
    }
}
