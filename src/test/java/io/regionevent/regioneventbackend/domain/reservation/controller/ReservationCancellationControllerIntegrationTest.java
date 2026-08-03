package io.regionevent.regioneventbackend.domain.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
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
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationCancellationControllerIntegrationTest {

    private static final int SESSION_CAPACITY = 5;
    private static final int HELD_QUANTITY = 2;
    private static final int RESERVED_REMAINING_CAPACITY = SESSION_CAPACITY - HELD_QUANTITY;

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final AuditEventRepository auditEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;
    private final List<Long> createdReservationIds = new ArrayList<>();
    private final List<Long> createdCapacityHoldIds = new ArrayList<>();
    private final List<Long> createdSessionIds = new ArrayList<>();
    private final List<Long> createdContentIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdRegionIds = new ArrayList<>();

    @Autowired
    ReservationCancellationControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        AuditEventRepository auditEventRepository,
        JdbcTemplate jdbcTemplate,
        JwtAccessTokenService jwtAccessTokenService,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.auditEventRepository = auditEventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.entityManager = entityManager;
    }

    @AfterEach
    void cleanUpFixture() {
        entityManager.clear();

        deleteReservationAuditEvents();
        deleteRows("DELETE FROM reservation WHERE reservation_id = ?", createdReservationIds);
        deleteRows("DELETE FROM capacity_hold WHERE hold_id = ?", createdCapacityHoldIds);
        deleteRows("DELETE FROM content_session WHERE session_id = ?", createdSessionIds);
        deleteRows("DELETE FROM content WHERE content_id = ?", createdContentIds);
        deleteRows("DELETE FROM user_role_assignment WHERE user_id = ?", createdUserIds);
        deleteRows("DELETE FROM app_user WHERE user_id = ?", createdUserIds);
        deleteRows("DELETE FROM region WHERE region_id = ?", createdRegionIds);

        entityManager.clear();
    }

    @Test
    void cancelReservation_whenConfirmedBeforeSessionStarts_cancelsRestoresCapacityAndRecordsAudit() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE, ReservationStatus.CONFIRMED, 3_600);

        performCancel(fixture.user(), fixture.reservation().getReservationId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("예약 취소에 성공했습니다."))
            .andExpect(jsonPath("$.data.reservationId").value(fixture.reservation().getReservationId().toString()))
            .andExpect(jsonPath("$.data.sessionId").value(fixture.session().getSessionId().toString()))
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))
            .andExpect(jsonPath("$.data.cancellationReason").value("USER_REQUEST"))
            .andExpect(jsonPath("$.data.cancelledAt").isNotEmpty())
            .andExpect(jsonPath("$.data.capacityReleasedAt").isNotEmpty());

        entityManager.clear();
        Reservation cancelledReservation = reservationRepository.findById(fixture.reservation().getReservationId())
            .orElseThrow();
        assertThat(cancelledReservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelledReservation.getCancellationReason()).isEqualTo("USER_REQUEST");
        assertThat(cancelledReservation.getCancelledAt()).isEqualTo(cancelledReservation.getCapacityReleasedAt());
        assertThat(getRemainingCapacity(fixture.session().getSessionId())).isEqualTo(SESSION_CAPACITY);
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getTargetType() == AuditEventTargetType.RESERVATION)
            .filteredOn(event -> event.getTargetId().equals(fixture.reservation().getReservationId()))
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getResult()).isEqualTo(AuditEventResult.SUCCESS);
                assertThat(event.getPreviousState()).isEqualTo("CONFIRMED");
                assertThat(event.getNextState()).isEqualTo("CANCELLED");
                assertThat(event.getReasonCode()).isEqualTo("USER_REQUEST");
                assertThat(event.getOccurredAt()).isEqualTo(cancelledReservation.getCancelledAt());
            });
    }

    @Test
    void cancelReservation_whenAlreadyCancelled_returnsFirstResultWithoutAdditionalChanges() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE, ReservationStatus.CONFIRMED, 3_600);

        performCancel(fixture.user(), fixture.reservation().getReservationId())
            .andExpect(status().isOk());
        entityManager.clear();
        Reservation firstCancelledReservation = reservationRepository.findById(fixture.reservation().getReservationId())
            .orElseThrow();
        Instant firstCancelledAt = firstCancelledReservation.getCancelledAt();
        Instant firstCapacityReleasedAt = firstCancelledReservation.getCapacityReleasedAt();

        performCancel(fixture.user(), fixture.reservation().getReservationId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))
            .andExpect(jsonPath("$.data.cancellationReason").value("USER_REQUEST"));

        entityManager.clear();
        Reservation retriedReservation = reservationRepository.findById(fixture.reservation().getReservationId())
            .orElseThrow();
        assertThat(retriedReservation.getCancelledAt()).isEqualTo(firstCancelledAt);
        assertThat(retriedReservation.getCapacityReleasedAt()).isEqualTo(firstCapacityReleasedAt);
        assertThat(getRemainingCapacity(fixture.session().getSessionId())).isEqualTo(SESSION_CAPACITY);
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getTargetType() == AuditEventTargetType.RESERVATION)
            .filteredOn(event -> event.getTargetId().equals(fixture.reservation().getReservationId()))
            .hasSize(1);
    }

    @Test
    void cancelReservation_whenReservationIdIsInvalid_returnsInputErrorsWithoutChanges() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE, ReservationStatus.CONFIRMED, 3_600);

        mockMvc.perform(post("/api/v1/me/reservations/{reservationId}/cancel", 0)
                .header("Authorization", bearerToken(fixture.user())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(post("/api/v1/me/reservations/{reservationId}/cancel", "not-a-number")
                .header("Authorization", bearerToken(fixture.user())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        mockMvc.perform(post("/api/v1/me/reservations/{reservationId}/cancel", "001")
                .header("Authorization", bearerToken(fixture.user())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(post("/api/v1/me/reservations/{reservationId}/cancel", "+1")
                .header("Authorization", bearerToken(fixture.user())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(post("/api/v1/me/reservations/{reservationId}/cancel", "9223372036854775808")
                .header("Authorization", bearerToken(fixture.user())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        assertReservationUnchanged(fixture);
    }

    @Test
    void cancelReservation_withoutAuthentication_returnsUnauthenticatedWithoutChanges() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE, ReservationStatus.CONFIRMED, 3_600);

        mockMvc.perform(post("/api/v1/me/reservations/{reservationId}/cancel", fixture.reservation().getReservationId()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertReservationUnchanged(fixture);
    }

    @Test
    void cancelReservation_whenMemberIsNotActive_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.WITHDRAWING, ReservationStatus.CONFIRMED, 3_600);

        performCancel(fixture.user(), fixture.reservation().getReservationId())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertReservationUnchanged(fixture);
    }

    @Test
    void cancelReservation_whenReservationIsOwnedByAnotherMember_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE, ReservationStatus.CONFIRMED, 3_600);
        AppUser anotherUser = saveVisitor(AppUserStatus.ACTIVE);

        performCancel(anotherUser, fixture.reservation().getReservationId())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertReservationUnchanged(fixture);
    }

    @Test
    void cancelReservation_whenReservationDoesNotExist_returnsNotFoundWithoutChanges() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE, ReservationStatus.CONFIRMED, 3_600);

        performCancel(fixture.user(), fixture.reservation().getReservationId() + 1_000_000)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertReservationUnchanged(fixture);
    }

    @Test
    void cancelReservation_whenReservationIsNotConfirmed_returnsConflictWithoutChanges() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE, ReservationStatus.CHECKED_IN, 3_600);

        performCancel(fixture.user(), fixture.reservation().getReservationId())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESERVATION_CANCEL_CONFLICT"));

        assertReservationUnchanged(fixture);
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getTargetType() == AuditEventTargetType.RESERVATION)
            .filteredOn(event -> event.getTargetId().equals(fixture.reservation().getReservationId()))
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getResult()).isEqualTo(AuditEventResult.FAILURE);
                assertThat(event.getPreviousState()).isEqualTo(ReservationStatus.CHECKED_IN.name());
                assertThat(event.getNextState()).isNull();
                assertThat(event.getReasonCode()).isEqualTo("RESERVATION_CANCEL_CONFLICT");
            });
    }

    @Test
    void cancelReservation_afterSessionStarts_returnsConflictWithoutChanges() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE, ReservationStatus.CONFIRMED, -60);

        performCancel(fixture.user(), fixture.reservation().getReservationId())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESERVATION_CANCEL_CONFLICT"));

        assertReservationUnchanged(fixture);
    }

    private org.springframework.test.web.servlet.ResultActions performCancel(AppUser user, Long reservationId)
        throws Exception {
        return mockMvc.perform(post("/api/v1/me/reservations/{reservationId}/cancel", reservationId)
            .header("Authorization", bearerToken(user)));
    }

    private Fixture createFixture(
        AppUserStatus userStatus,
        ReservationStatus reservationStatus,
        long sessionStartsInSeconds
    ) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        createdRegionIds.add(region.getRegionId());
        AppUser user = saveVisitor(userStatus);
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
        Instant startsAt = now.plusSeconds(sessionStartsInSeconds);
        ContentSession session = new ContentSession(
            content,
            region,
            startsAt,
            startsAt.plusSeconds(7_200),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(5_400),
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
            reservationStatus,
            now,
            null,
            null,
            null,
            null
        ));
        createdReservationIds.add(reservation.getReservationId());
        return new Fixture(user, session, reservation);
    }

    private AppUser saveVisitor(AppUserStatus status) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        AppUser user = appUserRepository.saveAndFlush(new AppUser(
            "visitor-" + suffix + "@example.com",
            "hashed-password",
            "예약 사용자",
            "010-1234-5678",
            status
        ));
        createdUserIds.add(user.getUserId());
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.VISITOR, null));
        return user;
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

    private void assertReservationUnchanged(Fixture fixture) {
        entityManager.clear();
        assertThat(reservationRepository.findById(fixture.reservation().getReservationId()))
            .hasValueSatisfying(reservation -> assertThat(reservation.getStatus())
                .isEqualTo(fixture.reservation().getStatus()));
        assertThat(getRemainingCapacity(fixture.session().getSessionId()))
            .isEqualTo(RESERVED_REMAINING_CAPACITY);
    }

    private int getRemainingCapacity(Long sessionId) {
        entityManager.clear();
        return contentSessionRepository.findById(sessionId).orElseThrow().getRemainingCapacity();
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private record Fixture(AppUser user, ContentSession session, Reservation reservation) {
    }
}
