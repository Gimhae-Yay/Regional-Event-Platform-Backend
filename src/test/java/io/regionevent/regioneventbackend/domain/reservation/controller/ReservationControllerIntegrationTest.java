package io.regionevent.regioneventbackend.domain.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

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
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.security.JwtAccessTokenService;

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
            .andExpect(jsonPath("$.code").value("RESERVATION_HOLD_CONFLICT"));

        assertThat(getRemainingCapacity(fixture.session().getSessionId())).isEqualTo(1);
        assertThat(capacityHoldRepository.count()).isZero();
    }

    @Test
    void createReservationHold_afterSessionStarts_returnsConflictWithoutChanges() throws Exception {
        Fixture fixture = createFixture(ContentStatus.PUBLISHED, AppUserStatus.ACTIVE, 2, -1);

        performCreate(fixture, 1)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESERVATION_HOLD_CONFLICT"));

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
        entityManager.clear();

        performCreate(fixture, 1)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESERVATION_HOLD_CONFLICT"));

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
        ContentSession session = contentSessionRepository.saveAndFlush(new ContentSession(
            content,
            region,
            startsAt,
            startsAt.plusSeconds(7_200),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(5_400),
            capacity
        ));
        return new Fixture(user, session);
    }

    private AppUser saveUser(String loginIdentifier, AppUserStatus status) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "Reservation User",
            "010-1234-5678",
            status
        ));
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
