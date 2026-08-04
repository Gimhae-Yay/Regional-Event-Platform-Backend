package io.regionevent.regioneventbackend.domain.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActorLinkService;
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
public class RegionAdminReservationSearchAuditAtomicityTest {

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final AuditEventRepository auditEventRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final JdbcTemplate jdbcTemplate;
    private final List<Long> createdReservationIds = new ArrayList<>();
    private final List<Long> createdCapacityHoldIds = new ArrayList<>();
    private final List<Long> createdSessionIds = new ArrayList<>();
    private final List<Long> createdContentIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdRegionIds = new ArrayList<>();

    @MockitoBean
    private AuditEventActorLinkService auditEventActorLinkService;

    @Autowired
    RegionAdminReservationSearchAuditAtomicityTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        AuditEventRepository auditEventRepository,
        JwtAccessTokenService jwtAccessTokenService,
        JdbcTemplate jdbcTemplate
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
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void registerUnixTimestamp() {
        jdbcTemplate.execute(
            "CREATE ALIAS IF NOT EXISTS UNIX_TIMESTAMP FOR \"io.regionevent.regioneventbackend.domain.reservation.controller.RegionAdminReservationSearchAuditAtomicityTest.unixTimestamp\""
        );
    }

    public static BigDecimal unixTimestamp(Timestamp timestamp) {
        return BigDecimal.valueOf(timestamp.getTime(), 3);
    }

    @AfterEach
    void cleanUp() {
        deleteReservationAuditEvents();
        deleteRows("DELETE FROM reservation WHERE reservation_id = ?", createdReservationIds);
        deleteRows("DELETE FROM capacity_hold WHERE hold_id = ?", createdCapacityHoldIds);
        deleteRows("DELETE FROM content_session WHERE session_id = ?", createdSessionIds);
        deleteRows("DELETE FROM content WHERE content_id = ?", createdContentIds);
        deleteRows("DELETE FROM user_role_assignment WHERE user_id = ?", createdUserIds);
        deleteRows("DELETE FROM app_user WHERE user_id = ?", createdUserIds);
        deleteRows("DELETE FROM region WHERE region_id = ?", createdRegionIds);
    }

    @Test
    void search_감사_처리자_연결이_실패하면_성공_응답과_감사를_롤백한다() throws Exception {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("audit actor link storage failure"))
            .when(auditEventActorLinkService)
            .record(any(AuditEvent.class), any());

        mockMvc.perform(get("/api/v1/region-admin/reservations/search")
                .queryParam("reservationNo", fixture.reservation().getReservationNo())
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        assertThat(auditEventRepository.findAll())
            .noneMatch(event -> fixture.reservation().getReservationId().equals(event.getTargetId()));
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        Region region = regionRepository.saveAndFlush(new Region("G" + suffix, "김해시", true));
        createdRegionIds.add(region.getRegionId());
        AppUser regionAdmin = saveUser("region-admin-" + suffix, "지역 관리자", "010-1111-2222");
        userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(regionAdmin, UserRole.REGION_ADMIN, region)
        );
        AppUser operator = saveUser("operator-" + suffix, "운영자", "010-9876-5432");
        AppUser participant = saveUser("participant-" + suffix, "김민수", "010-1234-5678");
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 가야문화 체험",
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
            now.minusSeconds(600),
            now.plusSeconds(3_600),
            now.minusSeconds(60),
            now.plusSeconds(1_800),
            10
        );
        session.approve(operator, now.minusSeconds(300));
        session = contentSessionRepository.saveAndFlush(session);
        createdSessionIds.add(session.getSessionId());
        CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            participant,
            1,
            CapacityHoldStatus.CONSUMED,
            now,
            now,
            null,
            null
        ));
        createdCapacityHoldIds.add(hold.getHoldId());
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "R" + suffix,
            UUID.randomUUID().toString(),
            region,
            hold,
            session,
            participant,
            ReservationStatus.CONFIRMED,
            now,
            null,
            null,
            null,
            null
        ));
        createdReservationIds.add(reservation.getReservationId());
        return new Fixture(regionAdmin, reservation);
    }

    private AppUser saveUser(
        String loginIdentifierPrefix,
        String name,
        String phone
    ) {
        AppUser user = appUserRepository.saveAndFlush(new AppUser(
            loginIdentifierPrefix + "@example.com",
            "hashed-password",
            name,
            phone,
            AppUserStatus.ACTIVE
        ));
        createdUserIds.add(user.getUserId());
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

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private record Fixture(AppUser regionAdmin, Reservation reservation) {
    }
}
