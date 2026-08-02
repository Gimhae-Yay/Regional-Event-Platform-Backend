package io.regionevent.regioneventbackend.domain.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.sql.Timestamp;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecordStatus;
import io.regionevent.regioneventbackend.domain.idempotency.repository.IdempotencyRecordRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
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
@Transactional
class ReservationControllerIntegrationTest {

    private final MockMvc mockMvc;
    private final AppUserRepository appUserRepository;
    private final RegionRepository regionRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final AuditEventRepository auditEventRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ReservationControllerIntegrationTest(
        MockMvc mockMvc,
        AppUserRepository appUserRepository,
        RegionRepository regionRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        IdempotencyRecordRepository idempotencyRecordRepository,
        AuditEventRepository auditEventRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        JwtAccessTokenService jwtAccessTokenService,
        EntityManager entityManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.appUserRepository = appUserRepository;
        this.regionRepository = regionRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.auditEventRepository = auditEventRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void createReservationHold_reservesCapacityAndCreatesActiveHold() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, AppUserStatus.ACTIVE, 3, 20);

        performCreate(fixture, 2)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.sessionId").value(fixture.session().getSessionId().toString()))
            .andExpect(jsonPath("$.data.quantity").value(2))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(getRemainingCapacity(fixture.session().getSessionId())).isEqualTo(1);
        assertThat(capacityHoldRepository.findAll())
            .singleElement()
            .satisfies(hold -> {
                assertThat(hold.getUser().getUserId()).isEqualTo(fixture.user().getUserId());
                assertThat(hold.getQuantity()).isEqualTo(2);
                assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE);
                assertThat(hold.getCreatedAt()).isNotNull();
                assertThat(hold.getExpiresAt()).isEqualTo(hold.getCreatedAt().plusSeconds(600));
            });
    }

    @Test
    void createReservationHold_whenSessionStartsBeforeHoldDuration_usesSessionStartAsExpiration() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, AppUserStatus.ACTIVE, 2, 5);

        performCreate(fixture, 1)
            .andExpect(status().isCreated());

        assertThat(capacityHoldRepository.findAll())
            .singleElement()
            .satisfies(hold -> assertThat(hold.getExpiresAt()).isEqualTo(fixture.session().getStartsAt()));
    }

    @Test
    void createReservationHold_whenRequestIsInvalid_doesNotChangeCapacityOrCreateHold() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, AppUserStatus.ACTIVE, 2, 20);

        mockMvc.perform(post("/api/v1/reservations")
                .header("Authorization", bearerToken(fixture.user()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sessionId": "not-a-number",
                      "quantity": 1
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(getRemainingCapacity(fixture.session().getSessionId())).isEqualTo(2);
        assertThat(capacityHoldRepository.count()).isZero();
    }

    @Test
    void createReservationHold_whenRequestJsonIsMalformed_doesNotChangeCapacityOrCreateHold() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, AppUserStatus.ACTIVE, 2, 20);

        mockMvc.perform(post("/api/v1/reservations")
                .header("Authorization", bearerToken(fixture.user()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));

        assertThat(getRemainingCapacity(fixture.session().getSessionId())).isEqualTo(2);
        assertThat(capacityHoldRepository.count()).isZero();
    }

    @Test
    void createReservationHold_withoutAuthentication_returnsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sessionId": "1",
                      "quantity": 1
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void createReservationHold_whenMemberIsNotActive_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, AppUserStatus.WITHDRAWING, 2, 20);

        performCreate(fixture, 1)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(getRemainingCapacity(fixture.session().getSessionId())).isEqualTo(2);
        assertThat(capacityHoldRepository.count()).isZero();
    }

    @Test
    void createReservationHold_whenContentIsNotPublished_returnsNotFoundWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PENDING, AppUserStatus.ACTIVE, 2, 20);

        performCreate(fixture, 1)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(contentSessionRepository.findById(fixture.session().getSessionId()).orElseThrow().getRemainingCapacity())
            .isEqualTo(2);
        assertThat(capacityHoldRepository.count()).isZero();
    }

    @Test
    void createReservationHold_whenCapacityIsInsufficient_returnsConflictWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, AppUserStatus.ACTIVE, 1, 20);

        performCreate(fixture, 2)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESERVATION_HOLD_CONFLICT"))
            .andExpect(jsonPath("$.message").value("예약 대기를 생성할 수 없는 상태입니다."));

        assertThat(getRemainingCapacity(fixture.session().getSessionId())).isEqualTo(1);
        assertThat(capacityHoldRepository.count()).isZero();
    }

    @Test
    void createReservationHold_afterSessionStarts_returnsConflictWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, AppUserStatus.ACTIVE, 2, -1);

        performCreate(fixture, 1)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESERVATION_HOLD_CONFLICT"))
            .andExpect(jsonPath("$.message").value("예약 대기를 생성할 수 없는 상태입니다."));

        assertThat(getRemainingCapacity(fixture.session().getSessionId())).isEqualTo(2);
        assertThat(capacityHoldRepository.count()).isZero();
    }

    @Test
    void createReservationHold_whenSessionIsNotScheduled_returnsConflictWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, AppUserStatus.ACTIVE, 2, 20);
        jdbcTemplate.update(
            "UPDATE content_session SET status = ?, completed_at = CURRENT_TIMESTAMP WHERE session_id = ?",
            "COMPLETED",
            fixture.session().getSessionId()
        );
        entityManager.flush();
        entityManager.clear();

        performCreate(fixture, 1)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESERVATION_HOLD_CONFLICT"))
            .andExpect(jsonPath("$.message").value("예약 대기를 생성할 수 없는 상태입니다."));

        assertThat(getRemainingCapacity(fixture.session().getSessionId())).isEqualTo(2);
        assertThat(capacityHoldRepository.count()).isZero();
    }

    @Test
    void createReservationHold_onRepeatedRequests_createsSeparateHolds() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, AppUserStatus.ACTIVE, 3, 20);

        performCreate(fixture, 1).andExpect(status().isCreated());
        performCreate(fixture, 1).andExpect(status().isCreated());

        assertThat(getRemainingCapacity(fixture.session().getSessionId())).isEqualTo(1);
        assertThat(capacityHoldRepository.findAll()).hasSize(2);
    }

    @Test
    void confirmReservation_consumesHoldWithoutChangingCapacityAndRecordsAudits() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, AppUserStatus.ACTIVE, 2, 20);
        performCreate(fixture, 1).andExpect(status().isCreated());
        CapacityHold capacityHold = capacityHoldRepository.findAll().getFirst();
        int remainingCapacityBeforeConfirm = getRemainingCapacity(fixture.session().getSessionId());

        performConfirm(fixture, capacityHold.getHoldId(), "confirm-success-key")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("무료 예약 확정에 성공했습니다."))
            .andExpect(jsonPath("$.data.holdId").value(capacityHold.getHoldId().toString()))
            .andExpect(jsonPath("$.data.sessionId").value(fixture.session().getSessionId().toString()))
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        entityManager.flush();
        entityManager.clear();
        assertThat(getRemainingCapacity(fixture.session().getSessionId())).isEqualTo(remainingCapacityBeforeConfirm);
        assertThat(capacityHoldRepository.findById(capacityHold.getHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.CONSUMED));
        assertThat(reservationRepository.findAll())
            .singleElement()
            .satisfies(reservation -> {
                assertThat(reservation.getCapacityHold().getHoldId()).isEqualTo(capacityHold.getHoldId());
                assertThat(reservation.getReservationNo()).matches("R\\d{8}[0-9ABCDEFGHJKMNPQRSTVWXYZ]{12}");
                assertThat(reservation.getQrReference()).matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
                );
            });
        assertThat(idempotencyRecordRepository.findAll())
            .singleElement()
            .satisfies(record -> {
                assertThat(record.getStatus()).isEqualTo(IdempotencyRecordStatus.SUCCEEDED);
                assertThat(record.getResultReservation()).isNotNull();
                assertThat(record.getIdempotencyKeyHash()).doesNotContain("confirm-success-key");
            });
        assertThat(auditEventRepository.findAll())
            .extracting(event -> event.getTargetType())
            .containsExactlyInAnyOrder(AuditEventTargetType.CAPACITY_HOLD, AuditEventTargetType.RESERVATION);
    }

    @Test
    void confirmReservation_withoutAuthentication_returnsUnauthenticatedWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, AppUserStatus.ACTIVE, 2, 20);
        performCreate(fixture, 1).andExpect(status().isCreated());
        CapacityHold capacityHold = capacityHoldRepository.findAll().getFirst();

        mockMvc.perform(post("/api/v1/reservation-holds/{holdId}/confirm", capacityHold.getHoldId())
                .header("Idempotency-Key", "missing-authentication-key"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(capacityHoldRepository.findById(capacityHold.getHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE));
        assertThat(reservationRepository.count()).isZero();
        assertThat(idempotencyRecordRepository.count()).isZero();
    }

    @Test
    void confirmReservation_withInvalidAccessToken_returnsUnauthenticatedWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, AppUserStatus.ACTIVE, 2, 20);
        performCreate(fixture, 1).andExpect(status().isCreated());
        CapacityHold capacityHold = capacityHoldRepository.findAll().getFirst();

        mockMvc.perform(post("/api/v1/reservation-holds/{holdId}/confirm", capacityHold.getHoldId())
                .header("Authorization", "Bearer invalid-access-token")
                .header("Idempotency-Key", "invalid-access-token-key"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(capacityHoldRepository.findById(capacityHold.getHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE));
        assertThat(reservationRepository.count()).isZero();
        assertThat(idempotencyRecordRepository.count()).isZero();
    }

    @Test
    void confirmReservation_whenMemberIsNotActive_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, AppUserStatus.WITHDRAWING, 2, 20);
        CapacityHold capacityHold = createActiveHold(fixture);

        performConfirm(fixture, capacityHold.getHoldId(), "inactive-member-key")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value(403))
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(capacityHoldRepository.findById(capacityHold.getHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE));
        assertThat(reservationRepository.count()).isZero();
        assertThat(idempotencyRecordRepository.count()).isZero();
    }

    @Test
    void confirmReservation_whenHoldIsOwnedByAnotherMember_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, AppUserStatus.ACTIVE, 2, 20);
        performCreate(fixture, 1).andExpect(status().isCreated());
        CapacityHold capacityHold = capacityHoldRepository.findAll().getFirst();
        AppUser anotherUser = saveUser("another-visitor-" + System.nanoTime() + "@example.com", AppUserStatus.ACTIVE);

        performConfirm(anotherUser, capacityHold.getHoldId(), "another-member-key")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value(403))
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(capacityHoldRepository.findById(capacityHold.getHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE));
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void confirmReservation_withSameKey_returnsStoredResultWithoutAdditionalChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, AppUserStatus.ACTIVE, 2, 20);
        performCreate(fixture, 1).andExpect(status().isCreated());
        CapacityHold capacityHold = capacityHoldRepository.findAll().getFirst();

        performConfirm(fixture, capacityHold.getHoldId(), "confirm-retry-key")
            .andExpect(status().isCreated());
        performConfirm(fixture, capacityHold.getHoldId(), "confirm-retry-key")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.holdId").value(capacityHold.getHoldId().toString()));

        assertThat(reservationRepository.count()).isOne();
        assertThat(idempotencyRecordRepository.count()).isOne();
        assertThat(auditEventRepository.count()).isEqualTo(2);
    }

    @Test
    void confirmReservation_withDifferentHoldForSameKey_returnsIdempotencyConflict() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, AppUserStatus.ACTIVE, 3, 20);
        performCreate(fixture, 1).andExpect(status().isCreated());
        performCreate(fixture, 1).andExpect(status().isCreated());
        CapacityHold firstHold = capacityHoldRepository.findAll().get(0);
        CapacityHold secondHold = capacityHoldRepository.findAll().get(1);

        performConfirm(fixture, firstHold.getHoldId(), "confirm-conflict-key")
            .andExpect(status().isCreated());
        performConfirm(fixture, secondHold.getHoldId(), "confirm-conflict-key")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));

        entityManager.clear();
        assertThat(capacityHoldRepository.findById(secondHold.getHoldId()))
            .hasValueSatisfying(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.ACTIVE));
        assertThat(reservationRepository.count()).isOne();
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getResult() == AuditEventResult.FAILURE)
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getTargetType()).isEqualTo(AuditEventTargetType.CAPACITY_HOLD);
                assertThat(event.getTargetId()).isEqualTo(secondHold.getHoldId());
                assertThat(event.getReasonCode()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
            });
    }

    @Test
    void confirmReservation_withConsumedHold_recordsAndReturnsConfirmationConflict() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, AppUserStatus.ACTIVE, 2, 20);
        performCreate(fixture, 1).andExpect(status().isCreated());
        CapacityHold capacityHold = capacityHoldRepository.findAll().getFirst();
        performConfirm(fixture, capacityHold.getHoldId(), "first-confirm-key")
            .andExpect(status().isCreated());

        performConfirm(fixture, capacityHold.getHoldId(), "consumed-hold-key")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESERVATION_CONFIRM_CONFLICT"));
        performConfirm(fixture, capacityHold.getHoldId(), "consumed-hold-key")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESERVATION_CONFIRM_CONFLICT"));

        assertThat(reservationRepository.count()).isOne();
        assertThat(idempotencyRecordRepository.findAll())
            .filteredOn(record -> record.getStatus() == IdempotencyRecordStatus.FAILED)
            .singleElement()
            .satisfies(record -> assertThat(record.getResultCode()).isEqualTo("RESERVATION_CONFIRM_CONFLICT"));
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getResult() == AuditEventResult.FAILURE)
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getTargetType()).isEqualTo(AuditEventTargetType.CAPACITY_HOLD);
                assertThat(event.getTargetId()).isEqualTo(capacityHold.getHoldId());
                assertThat(event.getReasonCode()).isEqualTo("RESERVATION_CONFIRM_CONFLICT");
            });
    }

    @Test
    void confirmReservation_withExpiredHold_recordsAndReturnsConfirmationConflict() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, AppUserStatus.ACTIVE, 2, 20);
        performCreate(fixture, 1).andExpect(status().isCreated());
        CapacityHold capacityHold = capacityHoldRepository.findAll().getFirst();
        jdbcTemplate.update(
            "UPDATE capacity_hold SET expires_at = ? WHERE hold_id = ?",
            Timestamp.from(Instant.now().minusSeconds(1)),
            capacityHold.getHoldId()
        );
        entityManager.clear();

        performConfirm(fixture, capacityHold.getHoldId(), "expired-hold-key")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESERVATION_CONFIRM_CONFLICT"));

        assertThat(reservationRepository.count()).isZero();
        assertThat(idempotencyRecordRepository.findAll())
            .singleElement()
            .satisfies(record -> {
                assertThat(record.getStatus()).isEqualTo(IdempotencyRecordStatus.FAILED);
                assertThat(record.getResultCode()).isEqualTo("RESERVATION_CONFIRM_CONFLICT");
            });
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> event.getResult() == AuditEventResult.FAILURE)
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getTargetType()).isEqualTo(AuditEventTargetType.CAPACITY_HOLD);
                assertThat(event.getTargetId()).isEqualTo(capacityHold.getHoldId());
                assertThat(event.getReasonCode()).isEqualTo("RESERVATION_CONFIRM_CONFLICT");
            });
    }

    private org.springframework.test.web.servlet.ResultActions performCreate(Fixture fixture, int quantity) throws Exception {
        return mockMvc.perform(post("/api/v1/reservations")
            .header("Authorization", bearerToken(fixture.user()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "sessionId": "%d",
                  "quantity": %d
                }
                """.formatted(fixture.session().getSessionId(), quantity)));
    }

    private org.springframework.test.web.servlet.ResultActions performConfirm(
        Fixture fixture,
        Long holdId,
        String idempotencyKey
    ) throws Exception {
        return performConfirm(fixture.user(), holdId, idempotencyKey);
    }

    private org.springframework.test.web.servlet.ResultActions performConfirm(
        AppUser user,
        Long holdId,
        String idempotencyKey
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/reservation-holds/{holdId}/confirm", holdId)
            .header("Authorization", bearerToken(user))
            .header("Idempotency-Key", idempotencyKey));
    }

    private CapacityHold createActiveHold(Fixture fixture) {
        return capacityHoldRepository.saveAndFlush(new CapacityHold(
            fixture.session().getRegion(),
            fixture.session(),
            fixture.user(),
            1,
            CapacityHoldStatus.ACTIVE,
            Instant.now().plusSeconds(600),
            null,
            null,
            null
        ));
    }

    private Fixture createFixture(
        ContentStatus contentStatus,
        AppUserStatus userStatus,
        int capacity,
        long startsInMinutes
    ) {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "Gimhae", true));
        AppUser operator = saveUser("operator-" + System.nanoTime() + "@example.com", AppUserStatus.ACTIVE);
        AppUser user = saveUser("visitor-" + System.nanoTime() + "@example.com", userStatus);
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            contentStatus,
            "Local experience",
            "Experience description",
            "Gimhae",
            "10:00-18:00",
            "055-123-4567",
            "Follow the safety guide.",
            "Age 7 or older",
            "Comfortable clothes",
            "Cancellation is allowed before the session.",
            Instant.now().minusSeconds(60)
        ));
        Instant startsAt = Instant.now().plusSeconds(startsInMinutes * 60);
        ContentSession session = new ContentSession(
            content,
            region,
            startsAt,
            startsAt.plusSeconds(7_200),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(5_400),
            capacity
        );
        session.approve(operator, Instant.now());
        session = contentSessionRepository.saveAndFlush(session);
        return new Fixture(user, session);
    }

    private AppUser saveUser(String loginIdentifier, AppUserStatus status) {
        AppUser user = appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "Reservation User",
            "010-1234-5678",
            status
        ));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.VISITOR, null));
        return user;
    }

    private int getRemainingCapacity(Long sessionId) {
        entityManager.clear();
        return contentSessionRepository.findById(sessionId).orElseThrow().getRemainingCapacity();
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private record Fixture(AppUser user, ContentSession session) {
    }
}
