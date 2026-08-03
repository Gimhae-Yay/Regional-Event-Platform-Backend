package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

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
@Import(OperatorContentSessionControllerIntegrationTest.FixedClockTestConfiguration.class)
@Transactional
class OperatorContentSessionControllerIntegrationTest {

    private static final Instant REVIEWED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant SESSION_STARTS_AT = Instant.parse("2030-08-10T01:00:00Z");
    private static final Instant HOLD_EXPIRES_AT = Instant.parse("2030-08-09T00:10:00Z");
    private static final Instant CONFIRMED_AT = Instant.parse("2030-08-09T00:00:00Z");
    private static final String CANCELLATION_REASON = "Session cancelled";

    private final MockMvc mockMvc;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final EntityManager entityManager;

    @Autowired
    OperatorContentSessionControllerIntegrationTest(
        MockMvc mockMvc,
        JwtAccessTokenService jwtAccessTokenService,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.entityManager = entityManager;
    }

    @Test
    void cancelSession_whenRequestIsValid_cancelsSessionHoldsAndConfirmedReservations() throws Exception {
        Fixture fixture = createFixture();
        CapacityHold activeHold = saveActiveHold(fixture, 2);
        Reservation confirmedReservation = saveReservation(fixture, 3, ReservationStatus.CONFIRMED);
        Reservation checkedInReservation = saveReservation(fixture, 1, ReservationStatus.CHECKED_IN);
        contentSessionRepository.decreaseRemainingCapacityIfReservable(
            fixture.contentSession().getSessionId(),
            6,
            ContentStatus.PUBLISHED,
            ContentSessionStatus.SCHEDULED
        );
        entityManager.flush();
        entityManager.clear();

        performCancel(fixture.operator(), fixture.contentSession().getSessionId().toString(), requestBody("  "
            + CANCELLATION_REASON
            + "  "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("회차 취소에 성공했습니다."))
            .andExpect(jsonPath("$.data.sessionId").value(fixture.contentSession().getSessionId().toString()))
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))
            .andExpect(jsonPath("$.data.cancellationReason").value(CANCELLATION_REASON))
            .andExpect(jsonPath("$.data.cancelledAt").isString());

        entityManager.flush();
        entityManager.clear();
        ContentSession cancelledSession = contentSessionRepository
            .findById(fixture.contentSession().getSessionId())
            .orElseThrow();
        CapacityHold invalidatedHold = capacityHoldRepository.findById(activeHold.getHoldId()).orElseThrow();
        Reservation cancelledReservation = reservationRepository
            .findById(confirmedReservation.getReservationId())
            .orElseThrow();
        Reservation unchangedCheckedInReservation = reservationRepository
            .findById(checkedInReservation.getReservationId())
            .orElseThrow();

        assertThat(cancelledSession.getStatus()).isEqualTo(ContentSessionStatus.CANCELLED);
        assertThat(cancelledSession.getCancellationReason()).isEqualTo(CANCELLATION_REASON);
        assertThat(cancelledSession.getCancelledByUser().getUserId()).isEqualTo(fixture.operator().getUserId());
        assertThat(cancelledSession.getRemainingCapacity()).isEqualTo(9);
        assertThat(invalidatedHold.getStatus()).isEqualTo(CapacityHoldStatus.INVALIDATED);
        assertThat(invalidatedHold.getInvalidationReason()).isEqualTo(CANCELLATION_REASON);
        assertThat(invalidatedHold.getCapacityReleasedAt()).isNotNull();
        assertThat(cancelledReservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelledReservation.getCancellationReason()).isEqualTo(CANCELLATION_REASON);
        assertThat(cancelledReservation.getCapacityReleasedAt()).isNotNull();
        assertThat(unchangedCheckedInReservation.getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
        assertThat(unchangedCheckedInReservation.getCancelledAt()).isNull();
        assertSuccessfulAudit(fixture, cancelledSession.getCancelledAt());
    }

    @Test
    void cancelSession_whenTrimmedReasonIs500Characters_cancelsSession() throws Exception {
        Fixture fixture = createFixture();
        String cancellationReason = "a".repeat(500);

        performCancel(
            fixture.operator(),
            fixture.contentSession().getSessionId().toString(),
            requestBody(" " + cancellationReason + " ")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.cancellationReason").value(cancellationReason));
    }

    @Test
    void cancelSession_whenSessionAlreadyStarted_cancelsConfirmedReservationWithoutCapacityRelease()
        throws Exception {

        Fixture fixture = createFixture(Instant.parse("2026-08-01T01:00:00Z"));
        Reservation confirmedReservation = saveReservation(fixture, 2, ReservationStatus.CONFIRMED);
        contentSessionRepository.decreaseRemainingCapacityIfReservable(
            fixture.contentSession().getSessionId(),
            2,
            ContentStatus.PUBLISHED,
            ContentSessionStatus.SCHEDULED
        );
        entityManager.flush();
        entityManager.clear();

        performCancel(fixture.operator(), fixture.contentSession().getSessionId().toString(), requestBody(
            CANCELLATION_REASON
        ))
            .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        ContentSession cancelledSession = contentSessionRepository
            .findById(fixture.contentSession().getSessionId())
            .orElseThrow();
        Reservation cancelledReservation = reservationRepository
            .findById(confirmedReservation.getReservationId())
            .orElseThrow();
        assertThat(cancelledSession.getStatus()).isEqualTo(ContentSessionStatus.CANCELLED);
        assertThat(cancelledSession.getRemainingCapacity()).isEqualTo(10);
        assertThat(cancelledReservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelledReservation.getCapacityReleasedAt()).isNull();
    }

    @Test
    void cancelSession_whenDatabaseTimeIsAfterSessionStart_doesNotReleaseReservationCapacity() throws Exception {
        Fixture fixture = createFixture(Instant.parse("2026-08-01T01:00:00Z"));
        Reservation confirmedReservation = saveReservation(fixture, 2, ReservationStatus.CONFIRMED);
        updateRemainingCapacity(fixture.contentSession().getSessionId(), 8);
        entityManager.flush();
        entityManager.clear();

        performCancel(fixture.operator(), fixture.contentSession().getSessionId().toString(), requestBody(
            CANCELLATION_REASON
        ))
            .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        ContentSession cancelledSession = contentSessionRepository
            .findById(fixture.contentSession().getSessionId())
            .orElseThrow();
        Reservation cancelledReservation = reservationRepository
            .findById(confirmedReservation.getReservationId())
            .orElseThrow();
        assertThat(cancelledSession.getStatus()).isEqualTo(ContentSessionStatus.CANCELLED);
        assertThat(cancelledSession.getRemainingCapacity()).isEqualTo(8);
        assertThat(cancelledReservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelledReservation.getCapacityReleasedAt()).isNull();
    }

    @Test
    void cancelSession_whenInputIsInvalid_returnsContractErrors() throws Exception {
        Fixture fixture = createFixture();

        performCancel(fixture.operator(), "abc", requestBody(CANCELLATION_REASON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        performCancel(fixture.operator(), "0", requestBody(CANCELLATION_REASON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        performCancel(fixture.operator(), fixture.contentSession().getSessionId().toString(), requestBody(" "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        performCancel(
            fixture.operator(),
            fixture.contentSession().getSessionId().toString(),
            requestBody("a".repeat(501))
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        performCancel(fixture.operator(), fixture.contentSession().getSessionId().toString(), "{")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));
    }

    @Test
    void cancelSession_whenAuthorizationFails_returnsForbiddenOrUnauthenticated() throws Exception {
        Fixture fixture = createFixture();
        AppUser visitor = saveUser("cancel-visitor@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(visitor, UserRole.VISITOR, null));
        AppUser otherOperator = saveUser("cancel-other-operator@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(otherOperator, UserRole.OPERATOR, fixture.region()));

        mockMvc.perform(post(
            "/api/v1/operator/sessions/{sessionId}/cancel",
            fixture.contentSession().getSessionId()
        )
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody(CANCELLATION_REASON)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        performCancel(visitor, fixture.contentSession().getSessionId().toString(), requestBody(CANCELLATION_REASON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        performCancel(otherOperator, fixture.contentSession().getSessionId().toString(), requestBody(CANCELLATION_REASON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void cancelSession_whenSessionDoesNotExistOrStateIsInvalid_returnsContractError() throws Exception {
        Fixture fixture = createFixture();
        ContentSession pendingSession = savePendingSession(fixture);

        performCancel(fixture.operator(), "999999999", requestBody(CANCELLATION_REASON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        performCancel(fixture.operator(), pendingSession.getSessionId().toString(), requestBody(CANCELLATION_REASON))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SESSION_NOT_CANCELLABLE"))
            .andExpect(jsonPath("$.message").value("취소할 수 없는 회차 상태입니다."));
    }

    private void assertSuccessfulAudit(Fixture fixture, Instant cancelledAt) {
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(auditEvent -> {
            assertThat(auditEvent.getRegion().getRegionId()).isEqualTo(fixture.region().getRegionId());
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT_SESSION);
            assertThat(auditEvent.getTargetId()).isEqualTo(fixture.contentSession().getSessionId());
            assertThat(auditEvent.getPreviousState()).isEqualTo("SCHEDULED");
            assertThat(auditEvent.getNextState()).isEqualTo("CANCELLED");
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(auditEvent.getReasonCode()).isNull();
            assertThat(auditEvent.getActorRole()).isEqualTo("OPERATOR");
            assertThat(auditEvent.getOccurredAt()).isEqualTo(cancelledAt);
            assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
                .hasValueSatisfying(actorLink ->
                    assertThat(actorLink.getActor().getUserId()).isEqualTo(fixture.operator().getUserId())
                );
        });
    }

    private ResultActions performCancel(AppUser operator, String sessionId, String requestBody) throws Exception {
        return mockMvc.perform(post("/api/v1/operator/sessions/{sessionId}/cancel", sessionId)
            .header(HttpHeaders.AUTHORIZATION, bearerToken(operator))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private String requestBody(String cancellationReason) {
        return """
            {
              "cancellationReason": "%s"
            }
            """.formatted(cancellationReason);
    }

    private Fixture createFixture() {
        return createFixture(SESSION_STARTS_AT);
    }

    private Fixture createFixture(Instant startsAt) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = saveRegion("CANCEL-" + suffix);
        AppUser operator = saveUser("cancel-operator-" + suffix + "@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        Content content = saveContent(region, operator);
        ContentSession contentSession = saveScheduledSession(content, region, operator, startsAt);
        return new Fixture(region, operator, contentSession);
    }

    private ContentSession savePendingSession(Fixture fixture) {
        return contentSessionRepository.saveAndFlush(new ContentSession(
            fixture.contentSession().getContent(),
            fixture.region(),
            SESSION_STARTS_AT.plusSeconds(86_400),
            SESSION_STARTS_AT.plusSeconds(93_600),
            SESSION_STARTS_AT.plusSeconds(84_600),
            SESSION_STARTS_AT.plusSeconds(91_800),
            10
        ));
    }

    private CapacityHold saveActiveHold(Fixture fixture, int quantity) {
        return capacityHoldRepository.saveAndFlush(new CapacityHold(
            fixture.region(),
            fixture.contentSession(),
            saveUser("active-hold-user-" + quantity + "@example.com"),
            quantity,
            CapacityHoldStatus.ACTIVE,
            HOLD_EXPIRES_AT,
            null,
            null,
            null
        ));
    }

    private Reservation saveReservation(Fixture fixture, int quantity, ReservationStatus status) {
        AppUser user = saveUser("reservation-user-" + status.name() + "-" + quantity + "@example.com");
        CapacityHold capacityHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            fixture.region(),
            fixture.contentSession(),
            user,
            quantity,
            CapacityHoldStatus.CONSUMED,
            HOLD_EXPIRES_AT,
            CONFIRMED_AT,
            null,
            null
        ));
        return reservationRepository.saveAndFlush(new Reservation(
            "R-" + System.nanoTime() + "-" + status.name(),
            "qr-" + System.nanoTime() + "-" + status.name(),
            fixture.region(),
            capacityHold,
            fixture.contentSession(),
            user,
            status,
            CONFIRMED_AT,
            null,
            null,
            null,
            null
        ));
    }

    private void updateRemainingCapacity(Long sessionId, int remainingCapacity) {
        entityManager.createNativeQuery("""
            UPDATE content_session
            SET remaining_capacity = ?
            WHERE session_id = ?
            """)
            .setParameter(1, remainingCapacity)
            .setParameter(2, sessionId)
            .executeUpdate();
    }

    private ContentSession saveScheduledSession(
        Content content,
        Region region,
        AppUser reviewer,
        Instant startsAt
    ) {
        ContentSession contentSession = new ContentSession(
            content,
            region,
            startsAt,
            startsAt.plusSeconds(7_200),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(5_400),
            10
        );
        contentSession.approve(reviewer, REVIEWED_AT);
        return contentSessionRepository.saveAndFlush(contentSession);
    }

    private Content saveContent(Region region, AppUser operator) {
        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "Original title",
            "Original description",
            "Original location",
            "Original hours",
            "055-000-0000",
            "Original precautions",
            "Original age",
            "Original materials",
            "Original policy",
            Instant.parse("2026-08-01T00:00:00Z")
        ));
    }

    private Region saveRegion(String regionCode) {
        return regionRepository.saveAndFlush(new Region(regionCode, regionCode, true));
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "User",
            "01012345678",
            AppUserStatus.ACTIVE
        ));
    }

    private record Fixture(
        Region region,
        AppUser operator,
        ContentSession contentSession
    ) {
    }

    @TestConfiguration
    static class FixedClockTestConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(REVIEWED_AT, ZoneOffset.UTC);
        }
    }
}
