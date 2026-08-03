package io.regionevent.regioneventbackend.domain.visit.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
import io.regionevent.regioneventbackend.global.security.qr.QrTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql(statements = """
    CREATE ALIAS IF NOT EXISTS UNIX_TIMESTAMP FOR "io.regionevent.regioneventbackend.domain.visit.controller.CheckInControllerIntegrationTest.unixTimestamp"
    """)
public class CheckInControllerIntegrationTest {

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final QrTokenService qrTokenService;

    @Autowired
    CheckInControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        JwtAccessTokenService jwtAccessTokenService,
        QrTokenService qrTokenService
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.qrTokenService = qrTokenService;
    }

    @Test
    void checkInByQr_whenRequestIsValid_returnsSuccessResponse() throws Exception {
        Fixture fixture = createFixture();
        QrTokenService.IssuedQrToken issuedQrToken = qrTokenService.issue(
            fixture.reservation().getQrReference(),
            fixture.session().getSessionId(),
            Instant.now(),
            fixture.session().getCheckinCloseAt()
        );

        mockMvc.perform(post("/api/v1/operator/check-ins")
                .header("Authorization", bearerToken(fixture.operator()))
                .header("Idempotency-Key", "qr-controller-success-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "qrToken": "%s"
                    }
                    """.formatted(issuedQrToken.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("QR 체크인에 성공했습니다."))
            .andExpect(jsonPath("$.data.visitId").value(Matchers.matchesPattern("^[1-9][0-9]*$")))
            .andExpect(jsonPath("$.data.reservationId").value(fixture.reservation().getReservationId().toString()))
            .andExpect(jsonPath("$.data.sessionId").value(fixture.session().getSessionId().toString()))
            .andExpect(jsonPath("$.data.reservationStatus").value("CHECKED_IN"))
            .andExpect(jsonPath("$.data.checkInMethod").value("QR"))
            .andExpect(jsonPath("$.data.checkedAt").value(Matchers.endsWith("Z")));
    }

    @Test
    void checkInByQr_whenUnversionedPathIsUsed_returnsSuccessResponse() throws Exception {
        Fixture fixture = createFixture();
        QrTokenService.IssuedQrToken issuedQrToken = qrTokenService.issue(
            fixture.reservation().getQrReference(),
            fixture.session().getSessionId(),
            Instant.now(),
            fixture.session().getCheckinCloseAt()
        );

        mockMvc.perform(post("/operator/check-ins")
                .header("Authorization", bearerToken(fixture.operator()))
                .header("Idempotency-Key", "qr-controller-unversioned-path-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "qrToken": "%s"
                    }
                    """.formatted(issuedQrToken.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.reservationId").value(fixture.reservation().getReservationId().toString()))
            .andExpect(jsonPath("$.data.sessionId").value(fixture.session().getSessionId().toString()))
            .andExpect(jsonPath("$.data.reservationStatus").value("CHECKED_IN"))
            .andExpect(jsonPath("$.data.checkInMethod").value("QR"));
    }

    @Test
    void checkInByQr_whenTokenIsInvalid_returnsQrVerificationFailedResponse() throws Exception {
        Fixture fixture = createFixture();

        mockMvc.perform(post("/api/v1/operator/check-ins")
                .header("Authorization", bearerToken(fixture.operator()))
                .header("Idempotency-Key", "qr-controller-failure-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "qrToken": "invalid-token"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value(400))
            .andExpect(jsonPath("$.code").value("QR_VERIFICATION_FAILED"))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void checkInByQr_whenIdempotencyKeyIsMissing_returnsInvalidInputResponse() throws Exception {
        Fixture fixture = createFixture();
        QrTokenService.IssuedQrToken issuedQrToken = qrTokenService.issue(
            fixture.reservation().getQrReference(),
            fixture.session().getSessionId(),
            Instant.now(),
            fixture.session().getCheckinCloseAt()
        );

        mockMvc.perform(post("/api/v1/operator/check-ins")
                .header("Authorization", bearerToken(fixture.operator()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "qrToken": "%s"
                    }
                    """.formatted(issuedQrToken.token())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void checkInByQr_whenQrTokenIsBlank_returnsInvalidInputResponse() throws Exception {
        Fixture fixture = createFixture();

        mockMvc.perform(post("/api/v1/operator/check-ins")
                .header("Authorization", bearerToken(fixture.operator()))
                .header("Idempotency-Key", "qr-controller-blank-token-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "qrToken": " "
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void checkInManually_whenRequestIsValid_returnsSuccessResponse() throws Exception {
        Fixture fixture = createFixture();

        mockMvc.perform(post("/api/v1/operator/check-ins/manual")
                .header("Authorization", bearerToken(fixture.operator()))
                .header("Idempotency-Key", "manual-controller-success-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reservationNo": "%s",
                      "reason": "QR_SCAN_FAILED"
                    }
                    """.formatted(fixture.reservation().getReservationNo())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("예약번호 보조 체크인에 성공했습니다."))
            .andExpect(jsonPath("$.data.visitId").value(Matchers.matchesPattern("^[1-9][0-9]*$")))
            .andExpect(jsonPath("$.data.reservationId").value(fixture.reservation().getReservationId().toString()))
            .andExpect(jsonPath("$.data.sessionId").value(fixture.session().getSessionId().toString()))
            .andExpect(jsonPath("$.data.reservationStatus").value("CHECKED_IN"))
            .andExpect(jsonPath("$.data.checkInMethod").value("RESERVATION_NUMBER"))
            .andExpect(jsonPath("$.data.checkedAt").value(Matchers.endsWith("Z")));
    }

    @Test
    void checkInManually_whenUnversionedPathIsUsed_returnsNotFound() throws Exception {
        Fixture fixture = createFixture();

        mockMvc.perform(post("/operator/check-ins/manual")
                .header("Authorization", bearerToken(fixture.operator()))
                .header("Idempotency-Key", "manual-controller-unversioned-path-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reservationNo": "%s",
                      "reason": "QR_SCAN_FAILED"
                    }
                    """.formatted(fixture.reservation().getReservationNo())))
            .andExpect(status().isNotFound());
    }

    @Test
    void checkInManually_whenIdempotencyKeyIsMissing_returnsInvalidInputResponse() throws Exception {
        Fixture fixture = createFixture();

        mockMvc.perform(post("/api/v1/operator/check-ins/manual")
                .header("Authorization", bearerToken(fixture.operator()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reservationNo": "%s",
                      "reason": "QR_SCAN_FAILED"
                    }
                    """.formatted(fixture.reservation().getReservationNo())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        Region region = regionRepository.saveAndFlush(new Region("C" + suffix, "Controller Test Region", true));
        AppUser operator = saveUser("operator-controller-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        AppUser user = saveUser("visitor-controller-" + suffix);
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "Controller Check-in Content",
            "Controller check-in content description",
            "Controller Test Region",
            "10:00-18:00",
            "055-123-4567",
            "Safety notice",
            "Age 7+",
            "Included",
            "Cancellation policy",
            now.minusSeconds(600)
        ));
        ContentSession session = new ContentSession(
            content,
            region,
            now.minusSeconds(600),
            now.plusSeconds(3_600),
            now.minusSeconds(60),
            now.plusSeconds(600),
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
            "RC-" + suffix,
            UUID.randomUUID().toString(),
            region,
            hold,
            session,
            user,
            ReservationStatus.CONFIRMED,
            now,
            null,
            null,
            null,
            null
        ));
        return new Fixture(operator, session, reservation);
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier + "@example.com",
            "hashed-password",
            "Test User",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    public static BigDecimal unixTimestamp(OffsetDateTime value) {
        return BigDecimal.valueOf(value.toInstant().toEpochMilli())
            .movePointLeft(3);
    }

    private record Fixture(
        AppUser operator,
        ContentSession session,
        Reservation reservation
    ) {
    }
}
