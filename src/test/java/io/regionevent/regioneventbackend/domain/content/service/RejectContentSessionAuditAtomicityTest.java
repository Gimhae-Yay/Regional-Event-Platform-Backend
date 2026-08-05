package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
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
import io.regionevent.regioneventbackend.support.jpa.AtomicityJpaTestConfiguration;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@DataJpaTest
@Import({AtomicityJpaTestConfiguration.class, RejectContentSessionUseCase.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class RejectContentSessionAuditAtomicityTest {

    private static final Instant STARTS_AT = Instant.parse("2030-08-10T01:00:00Z");

    private final RejectContentSessionUseCase rejectContentSessionUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final AuditEventRepository auditEventRepository;
    private final EntityManager entityManager;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    RejectContentSessionAuditAtomicityTest(
        RejectContentSessionUseCase rejectContentSessionUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        AuditEventRepository auditEventRepository,
        EntityManager entityManager
    ) {
        this.rejectContentSessionUseCase = rejectContentSessionUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.auditEventRepository = auditEventRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 반려하면_심사_정보만_기록하고_홀드나_예약을_생성하지_않는다() {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED);

        rejectContentSessionUseCase.reject(
            fixture.adminId(),
            fixture.sessionId(),
            "반려 사유",
            UUID.randomUUID()
        );

        entityManager.clear();
        ContentSession session = contentSessionRepository.findById(fixture.sessionId()).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(ContentSessionStatus.REJECTED);
        assertThat(session.getReviewedAt()).isNotNull();
        assertThat(session.getReviewedByUser().getUserId()).isEqualTo(fixture.adminId());
        assertThat(session.getRejectReason()).isEqualTo("반려 사유");
        assertThat(capacityHoldRepository.count()).isZero();
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void 콘텐츠가_심사_대상이_아니면_상태_충돌을_반환한다() {
        Fixture fixture = createFixture(ContentStatus.PENDING);

        assertThatThrownBy(() -> rejectContentSessionUseCase.reject(
            fixture.adminId(),
            fixture.sessionId(),
            "반려 사유",
            UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.SESSION_STATE_CONFLICT);
    }

    @Test
    void 감사_기록에_실패하면_회차_반려와_예약_대상_변경을_함께_롤백한다() {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED);
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> rejectContentSessionUseCase.reject(
            fixture.adminId(),
            fixture.sessionId(),
            "반려 사유",
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class);

        entityManager.clear();
        ContentSession session = contentSessionRepository.findById(fixture.sessionId()).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(ContentSessionStatus.PENDING);
        assertThat(session.getReviewedAt()).isNull();
        assertThat(session.getReviewedByUser()).isNull();
        assertThat(session.getRejectReason()).isNull();
        assertThat(capacityHoldRepository.count()).isZero();
        assertThat(reservationRepository.count()).isZero();
        assertThat(auditEventRepository.count()).isZero();
    }

    private Fixture createFixture(ContentStatus contentStatus) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser admin = saveUser("admin-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region)
        );
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
            "055-1234-5678",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            Instant.parse("2026-08-05T00:00:00Z")
        ));
        ContentSession session = contentSessionRepository.saveAndFlush(new ContentSession(
            content,
            region,
            STARTS_AT,
            STARTS_AT.plusSeconds(7_200),
            STARTS_AT.minusSeconds(1_800),
            STARTS_AT.plusSeconds(5_400),
            20
        ));
        return new Fixture(admin.getUserId(), session.getSessionId());
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

    private record Fixture(
        Long adminId,
        Long sessionId
    ) {
    }
}
