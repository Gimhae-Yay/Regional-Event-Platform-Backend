package io.regionevent.regioneventbackend.domain.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
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
@Transactional
public class RegionAdminReservationControllerIntegrationTest {

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    RegionAdminReservationControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
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
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void registerUnixTimestamp() {
        jdbcTemplate.execute(
            "CREATE ALIAS IF NOT EXISTS UNIX_TIMESTAMP FOR \"io.regionevent.regioneventbackend.domain.reservation.controller.RegionAdminReservationControllerIntegrationTest.unixTimestamp\""
        );
    }

    public static BigDecimal unixTimestamp(Timestamp timestamp) {
        return BigDecimal.valueOf(timestamp.getTime(), 3);
    }

    @Test
    void search_담당_지역_관리자가_정상_조회하면_마스킹된_결과와_감사를_반환한다() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE, ReservationStatus.CONFIRMED);

        mockMvc.perform(get("/api/v1/region-admin/reservations/search")
                .queryParam("reservationNo", fixture.reservation().getReservationNo())
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.reservationId").value(fixture.reservation().getReservationId().toString()))
            .andExpect(jsonPath("$.data.reservationNo").value(fixture.reservation().getReservationNo()))
            .andExpect(jsonPath("$.data.content.contentId").value(fixture.content().getContentId().toString()))
            .andExpect(jsonPath("$.data.content.title").value("김해 가야문화 체험"))
            .andExpect(jsonPath("$.data.participant.name").value("김*수"))
            .andExpect(jsonPath("$.data.participant.phone").value("010-****-5678"))
            .andExpect(jsonPath("$.data.checkIn.checkedIn").value(false))
            .andExpect(jsonPath("$.data.checkIn.canCheckIn").value(false))
            .andExpect(jsonPath("$.data.checkIn.checkedAt").isEmpty())
            .andExpect(jsonPath("$.data.qrReference").doesNotExist())
            .andExpect(content().string(not(containsString("김민수"))))
            .andExpect(content().string(not(containsString("010-1234-5678"))));

        AuditEvent auditEvent = auditEventRepository.findAll().stream()
            .filter(event -> fixture.reservation().getReservationId().equals(event.getTargetId()))
            .findFirst()
            .orElseThrow();
        assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.RESERVATION);
        assertThat(auditEvent.getRegion().getRegionId()).isEqualTo(fixture.region().getRegionId());
        assertThat(auditEvent.getReasonCode()).isEqualTo("QR_VERIFICATION_FAILED");
        assertThat(auditEvent.getActorKind()).isEqualTo("USER");
        assertThat(auditEvent.getActorRole()).isEqualTo(UserRole.REGION_ADMIN.name());
        assertThat(auditEvent.getOccurredAt()).isNotNull();
        assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId()))
            .map(link -> link.getActor().getUserId())
            .contains(fixture.regionAdmin().getUserId());
    }

    @Test
    void search_다른_담당_지역_관리자는_FORBIDDEN을_반환하고_감사를_남기지_않는다() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE, ReservationStatus.CONFIRMED);
        Region otherRegion = regionRepository.saveAndFlush(new Region("D" + System.nanoTime(), "동해시", true));
        AppUser otherRegionAdmin = saveRegionAdmin(otherRegion, AppUserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/region-admin/reservations/search")
                .queryParam("reservationNo", fixture.reservation().getReservationNo())
                .header("Authorization", bearerToken(otherRegionAdmin)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertNoReservationAudit(fixture.reservation());
    }

    @Test
    void search_비활성_지역_관리자는_FORBIDDEN을_반환하고_감사를_남기지_않는다() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.WITHDRAWING, ReservationStatus.CONFIRMED);

        mockMvc.perform(get("/api/v1/region-admin/reservations/search")
                .queryParam("reservationNo", fixture.reservation().getReservationNo())
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertNoReservationAudit(fixture.reservation());
    }

    @Test
    void search_지역_관리자_역할이_없으면_FORBIDDEN을_반환하고_감사를_남기지_않는다() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE, ReservationStatus.CONFIRMED);
        AppUser visitor = saveUser("visitor-" + System.nanoTime(), "방문자", "010-2222-3333", AppUserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/region-admin/reservations/search")
                .queryParam("reservationNo", fixture.reservation().getReservationNo())
                .header("Authorization", bearerToken(visitor)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertNoReservationAudit(fixture.reservation());
    }

    @Test
    void search_취소된_예약도_조회하지만_canCheckIn은_false다() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE, ReservationStatus.CANCELLED);

        mockMvc.perform(get("/api/v1/region-admin/reservations/search")
                .queryParam("reservationNo", fixture.reservation().getReservationNo())
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))
            .andExpect(jsonPath("$.data.checkIn.canCheckIn").value(false));
    }

    @Test
    void search_예약번호가_없으면_NOT_FOUND를_반환하고_감사를_남기지_않는다() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE, ReservationStatus.CONFIRMED);

        mockMvc.perform(get("/api/v1/region-admin/reservations/search")
                .queryParam("reservationNo", "R-NOT-FOUND")
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void search_예약번호가_없거나_공백이면_INVALID_INPUT을_반환한다() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE, ReservationStatus.CONFIRMED);

        mockMvc.perform(get("/api/v1/region-admin/reservations/search")
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/v1/region-admin/reservations/search")
                .queryParam("reservationNo", " ")
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void search_인증_정보가_없으면_UNAUTHENTICATED를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/region-admin/reservations/search")
                .queryParam("reservationNo", "R20260804ABCDEFGHJKLM"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private void assertNoReservationAudit(Reservation reservation) {
        assertThat(auditEventRepository.findAll())
            .noneMatch(event -> reservation.getReservationId().equals(event.getTargetId()));
    }

    private Fixture createFixture(
        AppUserStatus regionAdminStatus,
        ReservationStatus reservationStatus
    ) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        Region region = regionRepository.saveAndFlush(new Region("G" + suffix, "김해시", true));
        AppUser regionAdmin = saveRegionAdmin(region, regionAdminStatus);
        AppUser operator = saveUser("operator-" + suffix, "운영자", "010-9876-5432", AppUserStatus.ACTIVE);
        AppUser participant = saveUser("participant-" + suffix, "김민수", "010-1234-5678", AppUserStatus.ACTIVE);
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
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "R" + suffix,
            UUID.randomUUID().toString(),
            region,
            hold,
            session,
            participant,
            reservationStatus,
            now,
            reservationStatus == ReservationStatus.CANCELLED ? now : null,
            reservationStatus == ReservationStatus.CANCELLED ? "USER_REQUEST" : null,
            null,
            null
        ));
        return new Fixture(region, regionAdmin, content, reservation);
    }

    private AppUser saveRegionAdmin(Region region, AppUserStatus status) {
        AppUser regionAdmin = saveUser("region-admin-" + System.nanoTime(), "지역 관리자", "010-1111-2222", status);
        userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(regionAdmin, UserRole.REGION_ADMIN, region)
        );
        return regionAdmin;
    }

    private AppUser saveUser(
        String loginIdentifierPrefix,
        String name,
        String phone,
        AppUserStatus status
    ) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifierPrefix + "@example.com",
            "hashed-password",
            name,
            phone,
            status
        ));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private record Fixture(
        Region region,
        AppUser regionAdmin,
        Content content,
        Reservation reservation
    ) {
    }
}
