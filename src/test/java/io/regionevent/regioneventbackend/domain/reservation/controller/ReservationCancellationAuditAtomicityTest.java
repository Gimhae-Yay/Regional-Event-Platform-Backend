package io.regionevent.regioneventbackend.domain.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationCancellationUseCase;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;
import io.regionevent.regioneventbackend.support.jpa.AtomicityJpaTestConfiguration;

@DataJpaTest
@Import(AtomicityJpaTestConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class ReservationCancellationAuditAtomicityTest {

    private static final int SESSION_CAPACITY = 5;
    private static final int HELD_QUANTITY = 2;
    private static final int RESERVED_REMAINING_CAPACITY = SESSION_CAPACITY - HELD_QUANTITY;

    private final ReservationCancellationUseCase reservationCancellationUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final AuditEventRepository auditEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private final List<Long> createdReservationIds = new ArrayList<>();
    private final List<Long> createdCapacityHoldIds = new ArrayList<>();
    private final List<Long> createdSessionIds = new ArrayList<>();
    private final List<Long> createdContentIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdRegionIds = new ArrayList<>();

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    ReservationCancellationAuditAtomicityTest(
        ReservationCancellationUseCase reservationCancellationUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        AuditEventRepository auditEventRepository,
        JdbcTemplate jdbcTemplate,
        EntityManager entityManager
    ) {
        this.reservationCancellationUseCase = reservationCancellationUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.auditEventRepository = auditEventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    @Test
    void cancelReservation_whenAuditRecordingFails_rollsBackReservationAndCapacity() {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> reservationCancellationUseCase.cancel(
            fixture.user().getUserId(),
            fixture.reservation().getReservationId(),
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class);

        entityManager.clear();
        assertThat(reservationRepository.findById(fixture.reservation().getReservationId()))
            .hasValueSatisfying(reservation -> {
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
                assertThat(reservation.getCancelledAt()).isNull();
                assertThat(reservation.getCancellationReason()).isNull();
                assertThat(reservation.getCapacityReleasedAt()).isNull();
            });
        assertThat(contentSessionRepository.findById(fixture.session().getSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity())
                .isEqualTo(RESERVED_REMAINING_CAPACITY));
        assertThat(auditEventRepository.findAll())
            .noneMatch(event -> event.getTargetId().equals(fixture.reservation().getReservationId()));
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        createdRegionIds.add(region.getRegionId());
        AppUser user = appUserRepository.saveAndFlush(new AppUser(
            "visitor-" + suffix + "@example.com",
            "hashed-password",
            "예약 사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        createdUserIds.add(user.getUserId());
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.VISITOR, null));
        AppUser operator = appUserRepository.saveAndFlush(new AppUser(
            "operator-" + suffix + "@example.com",
            "hashed-password",
            "운영자",
            "010-9876-5432",
            AppUserStatus.ACTIVE
        ));
        createdUserIds.add(operator.getUserId());
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
            "안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 전까지 취소할 수 있습니다.",
            now
        ));
        createdContentIds.add(content.getContentId());
        ContentSession session = new ContentSession(
            content,
            region,
            now.plusSeconds(3_600),
            now.plusSeconds(10_800),
            now.plusSeconds(1_800),
            now.plusSeconds(9_000),
            SESSION_CAPACITY
        );
        session.approve(operator, now);
        session = contentSessionRepository.saveAndFlush(session);
        createdSessionIds.add(session.getSessionId());
        jdbcTemplate.update(
            "UPDATE content_session SET remaining_capacity = ? WHERE session_id = ?",
            RESERVED_REMAINING_CAPACITY,
            session.getSessionId()
        );
        CapacityHold capacityHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            user,
            HELD_QUANTITY,
            CapacityHoldStatus.CONSUMED,
            now.plusSeconds(600),
            now,
            null,
            null,
            now
        ));
        createdCapacityHoldIds.add(capacityHold.getHoldId());
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "R" + suffix,
            UUID.randomUUID().toString(),
            region,
            capacityHold,
            session,
            user,
            ReservationStatus.CONFIRMED,
            now,
            null,
            null,
            null,
            null
        ));
        createdReservationIds.add(reservation.getReservationId());
        return new Fixture(user, session, reservation);
    }

    private void deleteReservationAuditEvents() {
        createdReservationIds.forEach(reservationId -> {
            jdbcTemplate.update(
                "DELETE FROM audit_event_actor_link WHERE audit_event_id IN "
                    + "(SELECT audit_event_id FROM audit_event WHERE target_type = ? AND target_id = ?)",
                AuditEventTargetType.RESERVATION.name(),
                reservationId
            );
            jdbcTemplate.update(
                "DELETE FROM audit_event WHERE target_type = ? AND target_id = ?",
                AuditEventTargetType.RESERVATION.name(),
                reservationId
            );
        });
    }

    private void deleteRows(String sql, List<Long> ids) {
        ids.forEach(id -> jdbcTemplate.update(sql, id));
    }

    private record Fixture(AppUser user, ContentSession session, Reservation reservation) {
    }
}
