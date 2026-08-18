package io.regionevent.regioneventbackend.domain.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
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
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class RegionAdminQrExceptionControllerIntegrationTest {

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final VisitRepository visitRepository;
    private final AuditEventRepository auditEventRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    @Autowired
    RegionAdminQrExceptionControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        VisitRepository visitRepository,
        AuditEventRepository auditEventRepository,
        JwtAccessTokenService jwtAccessTokenService,
        JdbcTemplate jdbcTemplate,
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
        this.visitRepository = visitRepository;
        this.auditEventRepository = auditEventRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    @Test
    void get_담당_지역_관리자가_예약_연결_예외를_조회하면_마스킹된_예약을_반환하고_상태를_변경하지_않는다()
        throws Exception {
        Fixture fixture = createFixture(ReservationStatus.CONFIRMED);
        AuditEvent auditEvent = saveAuditEvent(
            fixture.region(),
            AuditEventTargetType.RESERVATION,
            fixture.reservation().getReservationId(),
            AuditEventResult.FAILURE,
            "QR_VERIFICATION_FAILED"
        );
        long auditCount = auditEventRepository.count();

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions/{exceptionId}", auditEvent.getAuditEventId())
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("QR 예외 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.exceptionId").value(auditEvent.getAuditEventId()))
            .andExpect(jsonPath("$.data.exceptionType").value("RESERVATION_NUMBER_LOOKUP"))
            .andExpect(jsonPath("$.data.reservationResolved").value(true))
            .andExpect(jsonPath("$.data.reservation.reservationId")
                .value(fixture.reservation().getReservationId()))
            .andExpect(jsonPath("$.data.reservation.contentId").value(fixture.content().getContentId()))
            .andExpect(jsonPath("$.data.reservation.sessionId").value(fixture.session().getSessionId()))
            .andExpect(jsonPath("$.data.reservation.participant.memberLinked").value(true))
            .andExpect(jsonPath("$.data.reservation.participant.name").value("김*수"))
            .andExpect(jsonPath("$.data.reservation.participant.phone").value("010-****-5678"))
            .andExpect(jsonPath("$.data.reservation.checkIn.checkedIn").value(false))
            .andExpect(jsonPath("$.data.reservation.checkIn.canCheckIn").value(false))
            .andExpect(content().string(not(containsString("김민수"))))
            .andExpect(content().string(not(containsString("010-1234-5678"))))
            .andExpect(content().string(not(containsString(fixture.reservation().getQrReference()))));

        entityManager.clear();
        Reservation unchanged = reservationRepository.findById(fixture.reservation().getReservationId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(auditEventRepository.count()).isEqualTo(auditCount);
    }

    @Test
    void get_비버전_별칭으로_예약_미해결_QR_예외를_조회한다() throws Exception {
        Fixture fixture = createFixture(ReservationStatus.CONFIRMED);
        AuditEvent auditEvent = saveAuditEvent(
            fixture.region(),
            AuditEventTargetType.RESERVATION,
            null,
            AuditEventResult.FAILURE,
            "QR_CHECK_IN_SIGNATURE_INVALID"
        );

        mockMvc.perform(get("/region-admin/qr-exceptions/{exceptionId}", auditEvent.getAuditEventId())
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.exceptionType").value("QR_CHECK_IN_FAILURE"))
            .andExpect(jsonPath("$.data.reservationResolved").value(false))
            .andExpect(jsonPath("$.data.reservation").isEmpty());
    }

    @Test
    void get_보조_체크인_성공_VISIT_감사를_예약으로_연결해_반환한다() throws Exception {
        Fixture fixture = createFixture(ReservationStatus.CHECKED_IN);
        Visit visit = visitRepository.saveAndFlush(new Visit(
            fixture.region(),
            fixture.reservation(),
            fixture.participant(),
            fixture.content(),
            fixture.session(),
            fixture.operator(),
            CheckinMethod.RESERVATION_NUMBER,
            Instant.parse("2026-08-01T01:02:00Z")
        ));
        AuditEvent auditEvent = saveAuditEvent(
            fixture.region(),
            AuditEventTargetType.VISIT,
            visit.getVisitId(),
            AuditEventResult.SUCCESS,
            "MANUAL_CHECK_IN_QR_SCAN_FAILED_SUCCESS"
        );

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions/{exceptionId}", auditEvent.getAuditEventId())
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.exceptionType").value("MANUAL_CHECK_IN"))
            .andExpect(jsonPath("$.data.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.reservation.checkIn.checkedIn").value(true))
            .andExpect(jsonPath("$.data.reservation.checkIn.canCheckIn").value(false))
            .andExpect(jsonPath("$.data.reservation.checkIn.checkedAt").exists());
    }

    @Test
    void get_exceptionId_검증_실패는_계약된_오류_코드를_반환한다() throws Exception {
        Fixture fixture = createFixture(ReservationStatus.CONFIRMED);

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions/0")
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions/abc")
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions/9223372036854775808")
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void get_인증_정보가_없으면_UNAUTHENTICATED를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions/1"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void get_다른_담당_지역_관리자는_FORBIDDEN을_반환한다(CapturedOutput output) throws Exception {
        Fixture fixture = createFixture(ReservationStatus.CONFIRMED);
        Region otherRegion = regionRepository.saveAndFlush(new Region("D" + System.nanoTime(), "동해시", true));
        AppUser otherRegionAdmin = saveRegionAdmin(otherRegion);
        AuditEvent auditEvent = saveAuditEvent(
            fixture.region(),
            AuditEventTargetType.RESERVATION,
            fixture.reservation().getReservationId(),
            AuditEventResult.FAILURE,
            "QR_VERIFICATION_FAILED"
        );

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions/{exceptionId}", auditEvent.getAuditEventId())
                .header("Authorization", bearerToken(otherRegionAdmin)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(output.getOut()).contains(
            "QR exception detail read. requestId=",
            "regionId=" + otherRegion.getRegionId()
                + ", exceptionId=" + auditEvent.getAuditEventId()
                + ", resultCode=FORBIDDEN"
        );
    }

    @Test
    void get_미존재_범위밖_지역없는_감사_이벤트는_NOT_FOUND를_반환한다(CapturedOutput output) throws Exception {
        Fixture fixture = createFixture(ReservationStatus.CONFIRMED);
        AuditEvent contentAuditEvent = saveAuditEvent(
            fixture.region(),
            AuditEventTargetType.CONTENT,
            fixture.content().getContentId(),
            AuditEventResult.SUCCESS,
            null
        );
        AuditEvent regionlessAuditEvent = saveAuditEvent(
            null,
            AuditEventTargetType.RESERVATION,
            fixture.reservation().getReservationId(),
            AuditEventResult.FAILURE,
            "QR_VERIFICATION_FAILED"
        );

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions/999999999")
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions/{exceptionId}", contentAuditEvent.getAuditEventId())
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions/{exceptionId}", regionlessAuditEvent.getAuditEventId())
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(output.getOut()).contains(
            "QR exception detail read. requestId=",
            "regionId=" + fixture.region().getRegionId()
                + ", exceptionId=999999999, resultCode=NOT_FOUND",
            "regionId=" + fixture.region().getRegionId()
                + ", exceptionId=" + contentAuditEvent.getAuditEventId()
                + ", resultCode=NOT_FOUND",
            "regionId=" + fixture.region().getRegionId()
                + ", exceptionId=" + regionlessAuditEvent.getAuditEventId()
                + ", resultCode=NOT_FOUND"
        );
    }

    @Test
    void get_예약번호_조회_VISIT_감사_계약_불일치는_INTERNAL_SERVER_ERROR를_반환한다(
        CapturedOutput output
    ) throws Exception {
        Fixture fixture = createFixture(ReservationStatus.CHECKED_IN);
        Visit visit = visitRepository.saveAndFlush(new Visit(
            fixture.region(),
            fixture.reservation(),
            fixture.participant(),
            fixture.content(),
            fixture.session(),
            fixture.operator(),
            CheckinMethod.RESERVATION_NUMBER,
            Instant.parse("2026-08-01T01:02:00Z")
        ));
        AppUser otherParticipant = saveUser("other-participant-" + System.nanoTime(), "박민수", "010-2222-3333");
        AuditEvent auditEvent = saveAuditEvent(
            fixture.region(),
            AuditEventTargetType.VISIT,
            visit.getVisitId(),
            AuditEventResult.FAILURE,
            "QR_VERIFICATION_FAILED"
        );
        jdbcTemplate.update(
            "UPDATE visit SET user_id = ? WHERE visit_id = ?",
            otherParticipant.getUserId(),
            visit.getVisitId()
        );
        entityManager.clear();

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions/{exceptionId}", auditEvent.getAuditEventId())
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        assertThat(output.getOut()).doesNotContain(
            "QR exception detail read. requestId=",
            "resultCode=INTERNAL_SERVER_ERROR",
            otherParticipant.getName(),
            otherParticipant.getPhone()
        );
    }

    @Test
    void get_성공_로그에는_요청_지역_예외와_결과_코드만_남긴다(CapturedOutput output) throws Exception {
        Fixture fixture = createFixture(ReservationStatus.CONFIRMED);
        AuditEvent auditEvent = saveAuditEvent(
            fixture.region(),
            AuditEventTargetType.RESERVATION,
            fixture.reservation().getReservationId(),
            AuditEventResult.FAILURE,
            "QR_VERIFICATION_FAILED"
        );

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions/{exceptionId}", auditEvent.getAuditEventId())
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isOk());

        assertThat(output.getOut()).contains(
            "QR exception detail read. requestId=",
            "regionId=" + fixture.region().getRegionId()
                + ", exceptionId=" + auditEvent.getAuditEventId()
                + ", resultCode=SUCCESS"
        ).doesNotContain(
            fixture.participant().getName(),
            fixture.participant().getPhone(),
            fixture.reservation().getQrReference(),
            "qrReference",
            "userId"
        );
    }

    @Test
    void get_실패_로그에는_잘못된_원문_exceptionId를_남기지_않는다(CapturedOutput output) throws Exception {
        Fixture fixture = createFixture(ReservationStatus.CONFIRMED);
        String sensitiveExceptionId = "someone@example.com";

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions/{exceptionId}", sensitiveExceptionId)
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions/{exceptionId}", sensitiveExceptionId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(output.getOut()).contains(
            "QR exception detail read. requestId=",
            "regionId=null, exceptionId=null, resultCode=INVALID_TYPE",
            "regionId=null, exceptionId=null, resultCode=UNAUTHENTICATED",
            "HTTP request completed. method=GET, uri=/api/v1/region-admin/qr-exceptions/{exceptionId}, status=401"
        ).doesNotContain(sensitiveExceptionId);
    }

    private AuditEvent saveAuditEvent(
        Region region,
        AuditEventTargetType targetType,
        Long targetId,
        AuditEventResult result,
        String reasonCode
    ) {
        return auditEventRepository.saveAndFlush(new AuditEvent(
            UUID.randomUUID().toString(),
            region,
            targetType,
            targetId,
            null,
            result == AuditEventResult.SUCCESS ? "CHECKED_IN" : null,
            result,
            reasonCode,
            "USER",
            "OPERATOR",
            Instant.parse("2026-08-01T01:02:00Z")
        ));
    }

    private Fixture createFixture(ReservationStatus reservationStatus) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.parse("2026-08-01T00:30:00Z");
        Region region = regionRepository.saveAndFlush(new Region("G" + suffix, "김해시", true));
        AppUser regionAdmin = saveRegionAdmin(region);
        AppUser operator = saveUser("operator-" + suffix, "운영자", "010-9876-5432");
        AppUser participant = saveUser("participant-" + suffix, "김민수", "010-1234-5678");
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 도자기 체험",
            "김해 도자기를 체험하는 행사입니다.",
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
            now.plusSeconds(1_800),
            now.plusSeconds(5_400),
            now,
            now.plusSeconds(3_600),
            10
        );
        session.approve(operator, now);
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
            null,
            null,
            null,
            null
        ));
        return new Fixture(region, regionAdmin, operator, participant, content, session, reservation);
    }

    private AppUser saveRegionAdmin(Region region) {
        AppUser regionAdmin = saveUser("region-admin-" + System.nanoTime(), "지역 관리자", "010-1111-2222");
        userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(regionAdmin, UserRole.REGION_ADMIN, region)
        );
        return regionAdmin;
    }

    private AppUser saveUser(
        String loginIdentifierPrefix,
        String name,
        String phone
    ) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifierPrefix + "@example.com",
            "hashed-password",
            name,
            phone,
            AppUserStatus.ACTIVE
        ));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, user.getUserId());
    }

    private record Fixture(
        Region region,
        AppUser regionAdmin,
        AppUser operator,
        AppUser participant,
        Content content,
        ContentSession session,
        Reservation reservation
    ) {
    }
}
