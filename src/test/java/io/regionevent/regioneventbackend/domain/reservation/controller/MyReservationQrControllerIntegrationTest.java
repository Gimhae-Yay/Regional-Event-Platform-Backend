package io.regionevent.regioneventbackend.domain.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class MyReservationQrControllerIntegrationTest {

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final AuditEventRepository auditEventRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;

    @Autowired
    MyReservationQrControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        AuditEventRepository auditEventRepository,
        JwtAccessTokenService jwtAccessTokenService,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.auditEventRepository = auditEventRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void registerUnixTimestamp() {
        entityManager.createNativeQuery(
            "CREATE ALIAS IF NOT EXISTS UNIX_TIMESTAMP FOR \"io.regionevent.regioneventbackend.domain.reservation.controller.MyReservationQrControllerIntegrationTest.unixTimestamp\""
        ).executeUpdate();
    }

    public static BigDecimal unixTimestamp(Timestamp timestamp) {
        return BigDecimal.valueOf(timestamp.getTime(), 3);
    }

    @Test
    void get_whenEligible_returnsNoStoreQrWithoutStateChanges() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE, ReservationStatus.CONFIRMED, true);
        Long reservationId = fixture.reservation().getReservationId();
        Long sessionId = fixture.session().getSessionId();
        entityManager.clear();
        Instant reservationUpdatedAt = reservationRepository.findById(reservationId).orElseThrow().getUpdatedAt();
        int sessionVersion = contentSessionRepository.findById(sessionId).orElseThrow().getVersionNo();

        mockMvc.perform(get("/api/v1/me/reservations/{reservationId}/qr", reservationId)
                .header("Authorization", bearerToken(fixture.user())))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.reservationId").value(reservationId))
            .andExpect(jsonPath("$.data.sessionId").value(sessionId))
            .andExpect(jsonPath("$.data.qrToken").value(org.hamcrest.Matchers.startsWith("v1.qr-test-key.")));

        entityManager.clear();
        assertThat(reservationRepository.findById(reservationId))
            .hasValueSatisfying(reservation -> {
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
                assertThat(reservation.getUpdatedAt()).isEqualTo(reservationUpdatedAt.truncatedTo(ChronoUnit.MICROS));
            });
        assertThat(contentSessionRepository.findById(sessionId))
            .hasValueSatisfying(session -> assertThat(session.getVersionNo()).isEqualTo(sessionVersion));
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void get_whenRequesterIsNotReservationOwner_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE, ReservationStatus.CONFIRMED, true);
        AppUser anotherUser = saveUser(AppUserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/me/reservations/{reservationId}/qr", fixture.reservation().getReservationId())
                .header("Authorization", bearerToken(anotherUser)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertUnchanged(fixture);
    }

    @Test
    void get_whenRequesterIsNotActive_returnsForbiddenWithoutChanges() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.WITHDRAWING, ReservationStatus.CONFIRMED, true);

        mockMvc.perform(get("/api/v1/me/reservations/{reservationId}/qr", fixture.reservation().getReservationId())
                .header("Authorization", bearerToken(fixture.user())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertUnchanged(fixture);
    }

    @Test
    void get_whenReservationHasNoUser_returnsConflictWithoutChanges() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE, ReservationStatus.CONFIRMED, true, false);

        mockMvc.perform(get("/api/v1/me/reservations/{reservationId}/qr", fixture.reservation().getReservationId())
                .header("Authorization", bearerToken(fixture.user())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("QR_ISSUE_CONFLICT"));

        assertUnchanged(fixture);
    }

    @Test
    void get_whenReservationCannotIssueQr_returnsConflictWithoutChanges() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE, ReservationStatus.CHECKED_IN, true);

        mockMvc.perform(get("/api/v1/me/reservations/{reservationId}/qr", fixture.reservation().getReservationId())
                .header("Authorization", bearerToken(fixture.user())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("QR_ISSUE_CONFLICT"));

        assertUnchanged(fixture);
    }

    @Test
    void get_whenCheckinWindowIsNotOpen_returnsConflictWithoutChanges() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE, ReservationStatus.CONFIRMED, false);

        mockMvc.perform(get("/api/v1/me/reservations/{reservationId}/qr", fixture.reservation().getReservationId())
                .header("Authorization", bearerToken(fixture.user())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("QR_ISSUE_CONFLICT"));

        assertUnchanged(fixture);
    }

    @Test
    void get_whenReservationDoesNotExist_returnsNotFound() throws Exception {
        AppUser user = saveUser(AppUserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/me/reservations/999999/qr")
                .header("Authorization", bearerToken(user)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void get_whenReservationIdIsInvalid_returnsInputError() throws Exception {
        AppUser user = saveUser(AppUserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/me/reservations/0/qr")
                .header("Authorization", bearerToken(user)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/me/reservations/not-a-number/qr")
                .header("Authorization", bearerToken(user)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    private void assertUnchanged(Fixture fixture) {
        entityManager.clear();
        assertThat(reservationRepository.findById(fixture.reservation().getReservationId()))
            .hasValueSatisfying(reservation -> assertThat(reservation.getStatus()).isEqualTo(fixture.reservation().getStatus()));
        assertThat(contentSessionRepository.findById(fixture.session().getSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getStatus()).isEqualTo(fixture.session().getStatus()));
        assertThat(auditEventRepository.count()).isZero();
    }

    private Fixture createFixture(
        AppUserStatus userStatus,
        ReservationStatus reservationStatus,
        boolean checkinWindowOpen
    ) {
        return createFixture(userStatus, reservationStatus, checkinWindowOpen, true);
    }

    private Fixture createFixture(
        AppUserStatus userStatus,
        ReservationStatus reservationStatus,
        boolean checkinWindowOpen,
        boolean hasReservationUser
    ) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser operator = saveUser(AppUserStatus.ACTIVE);
        AppUser user = saveUser(userStatus);
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.SUSPENDED,
            "QR 조회 콘텐츠",
            "QR 조회 콘텐츠 설명",
            "김해시",
            "10:00-18:00",
            "055-123-4567",
            "안내",
            "만 7세 이상",
            "편한 복장",
            "취소 정책",
            now.minusSeconds(60)
        ));
        Instant checkinOpenAt = checkinWindowOpen ? now.minusSeconds(60) : now.plusSeconds(60);
        ContentSession session = new ContentSession(
            content,
            region,
            now.minusSeconds(600),
            now.plusSeconds(3_600),
            checkinOpenAt,
            now.plusSeconds(300),
            10
        );
        session.approve(operator, now.minusSeconds(300));
        session = contentSessionRepository.saveAndFlush(session);
        CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            user,
            1,
            CapacityHoldStatus.CONSUMED,
            now,
            now,
            null,
            null
        ));
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "R-" + suffix,
            UUID.randomUUID().toString(),
            region,
            hold,
            session,
            hasReservationUser ? user : null,
            reservationStatus,
            now,
            null,
            null,
            null,
            null
        ));
        return new Fixture(user, session, reservation);
    }

    private AppUser saveUser(AppUserStatus status) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            "visitor-" + suffix + "@example.com",
            "hashed-password",
            "방문자",
            "010-1234-5678",
            status
        ));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private record Fixture(AppUser user, ContentSession session, Reservation reservation) {
    }
}
