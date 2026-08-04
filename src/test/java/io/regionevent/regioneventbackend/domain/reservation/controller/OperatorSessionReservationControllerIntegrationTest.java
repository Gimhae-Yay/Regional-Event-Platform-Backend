package io.regionevent.regioneventbackend.domain.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class OperatorSessionReservationControllerIntegrationTest {

    private static final Instant SESSION_STARTS_AT = Instant.parse("2030-08-10T01:00:00Z");
    private static final Instant SESSION_ENDS_AT = Instant.parse("2030-08-10T03:00:00Z");
    private static final Instant CHECKIN_OPEN_AT = Instant.parse("2030-08-10T00:30:00Z");
    private static final Instant CHECKIN_CLOSE_AT = Instant.parse("2030-08-10T01:30:00Z");
    private static final Instant FIRST_CONFIRMED_AT = Instant.parse("2030-08-01T01:00:00Z");
    private static final Instant TIED_CONFIRMED_AT = Instant.parse("2030-08-02T01:00:00Z");
    private static final int HELD_QUANTITY = 2;

    private final MockMvc mockMvc;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final VisitRepository visitRepository;
    private final AuditEventRepository auditEventRepository;
    private final EntityManager entityManager;

    @Autowired
    OperatorSessionReservationControllerIntegrationTest(
        MockMvc mockMvc,
        JwtAccessTokenService jwtAccessTokenService,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        VisitRepository visitRepository,
        AuditEventRepository auditEventRepository,
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
        this.visitRepository = visitRepository;
        this.auditEventRepository = auditEventRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 회차별_예약자_목록_모든_예약_상태와_마스킹된_참여자_체크인_정보를_반환한다() throws Exception {
        Fixture fixture = createFixture();
        AppUser participant = saveUser("김민수", "010-1000-0001");
        Reservation confirmed = saveReservation(
            fixture,
            participant,
            ReservationStatus.CONFIRMED,
            FIRST_CONFIRMED_AT,
            "confirmed"
        );
        Reservation checkedIn = saveReservation(
            fixture,
            participant,
            ReservationStatus.CHECKED_IN,
            TIED_CONFIRMED_AT,
            "checked-in"
        );
        Reservation cancelled = saveReservation(
            fixture,
            participant,
            ReservationStatus.CANCELLED,
            TIED_CONFIRMED_AT,
            "cancelled"
        );
        Reservation expired = saveReservation(
            fixture,
            participant,
            ReservationStatus.EXPIRED,
            TIED_CONFIRMED_AT,
            "expired"
        );
        Reservation unlinked = saveReservation(
            fixture,
            null,
            ReservationStatus.CONFIRMED,
            TIED_CONFIRMED_AT.plusSeconds(1),
            "unlinked"
        );
        visitRepository.saveAndFlush(new Visit(
            fixture.region(),
            checkedIn,
            participant,
            fixture.content(),
            fixture.session(),
            fixture.owner(),
            CheckinMethod.QR,
            TIED_CONFIRMED_AT.plusSeconds(300)
        ));
        entityManager.flush();
        entityManager.clear();

        ResultActions result = performGet(
            fixture.owner(),
            fixture.content().getContentId(),
            fixture.session().getSessionId().toString()
        );

        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("회차별 예약자 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(fixture.content().getContentId().toString()))
            .andExpect(jsonPath("$.data.session.sessionId").value(fixture.session().getSessionId().toString()))
            .andExpect(jsonPath("$.data.session.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.data.session.startsAt").value("2030-08-10T10:00:00+09:00"))
            .andExpect(jsonPath("$.data.session.endsAt").value("2030-08-10T12:00:00+09:00"))
            .andExpect(jsonPath("$.data.reservations.length()").value(5))
            .andExpect(jsonPath("$.data.reservations[0].reservationId").value(confirmed.getReservationId().toString()))
            .andExpect(jsonPath("$.data.reservations[0].status").value("CONFIRMED"))
            .andExpect(jsonPath("$.data.reservations[1].reservationId").value(checkedIn.getReservationId().toString()))
            .andExpect(jsonPath("$.data.reservations[1].status").value("CHECKED_IN"))
            .andExpect(jsonPath("$.data.reservations[1].checkIn.checkedIn").value(true))
            .andExpect(jsonPath("$.data.reservations[1].checkIn.checkedAt").value("2030-08-02T01:05:00Z"))
            .andExpect(jsonPath("$.data.reservations[2].reservationId").value(cancelled.getReservationId().toString()))
            .andExpect(jsonPath("$.data.reservations[2].status").value("CANCELLED"))
            .andExpect(jsonPath("$.data.reservations[3].reservationId").value(expired.getReservationId().toString()))
            .andExpect(jsonPath("$.data.reservations[3].status").value("EXPIRED"))
            .andExpect(jsonPath("$.data.reservations[4].reservationId").value(unlinked.getReservationId().toString()))
            .andExpect(jsonPath("$.data.reservations[4].participant.name").value("탈퇴한 사용자"))
            .andExpect(jsonPath("$.data.reservations[4].participant.phone").doesNotExist())
            .andExpect(jsonPath("$.data.reservations[4].checkIn.checkedIn").value(false))
            .andExpect(jsonPath("$.data.reservations[4].checkIn.checkedAt").doesNotExist());

        String responseBody = result.andReturn().getResponse().getContentAsString();
        assertThat(responseBody)
            .contains("김*수", "010-****-0001")
            .doesNotContain("김민수", "010-1000-0001", "qr-confirmed", "userId");
        assertReadDoesNotChangeState(fixture, List.of(confirmed, checkedIn, cancelled, expired, unlinked));
    }

    @Test
    void 회차별_예약자_목록_예약이_없으면_빈_배열을_반환한다() throws Exception {
        Fixture fixture = createFixture();

        performGet(fixture.owner(), fixture.content().getContentId(), fixture.session().getSessionId().toString())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reservations").isArray())
            .andExpect(jsonPath("$.data.reservations").isEmpty());

        assertThat(auditEventRepository.findAll()).isEmpty();
    }

    @Test
    void 회차별_예약자_목록_소유자나_지역_권한이_아니면_FORBIDDEN을_반환한다() throws Exception {
        Fixture fixture = createFixture();
        AppUser otherOwner = saveOperator(fixture.region(), "다른 운영자");
        Region otherRegion = regionRepository.saveAndFlush(new Region("D" + System.nanoTime(), "동해시", true));
        AppUser otherRegionOperator = saveOperator(otherRegion, "타지역 운영자");
        AppUser withdrawingOperator = saveWithdrawingOperator(fixture.region(), "탈퇴 중 운영자");
        AppUser visitor = saveUser("방문자", "010-2000-0001");

        performGet(otherOwner, fixture.content().getContentId(), fixture.session().getSessionId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        performGet(otherRegionOperator, fixture.content().getContentId(), fixture.session().getSessionId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        performGet(withdrawingOperator, fixture.content().getContentId(), fixture.session().getSessionId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        performGet(visitor, fixture.content().getContentId(), fixture.session().getSessionId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 회차별_예약자_목록_콘텐츠와_회차가_일치하지_않거나_입력이_잘못되면_계약_오류를_반환한다()
        throws Exception {

        Fixture fixture = createFixture();
        Content otherContent = saveContent(fixture.region(), fixture.owner(), "다른 콘텐츠");
        ContentSession otherSession = saveScheduledSession(otherContent, fixture.region(), fixture.owner());

        performGet(fixture.owner(), fixture.content().getContentId(), otherSession.getSessionId().toString())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        performGet(fixture.owner(), fixture.content().getContentId() + 1_000_000L, fixture.session().getSessionId().toString())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        performGet(fixture.owner(), 0L, fixture.session().getSessionId().toString())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        performGet(fixture.owner(), fixture.content().getContentId(), "abc")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/operator/contents/{contentId}/reservations", fixture.content().getContentId())
            .header(HttpHeaders.AUTHORIZATION, bearerToken(fixture.owner())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 회차별_예약자_목록_대상이_PENDING_또는_REJECTED면_NOT_FOUND를_반환한다() throws Exception {
        for (ContentSessionStatus sessionStatus : List.of(
            ContentSessionStatus.PENDING,
            ContentSessionStatus.REJECTED
        )) {
            Fixture fixture = createFixture(sessionStatus);

            performGet(fixture.owner(), fixture.content().getContentId(), fixture.session().getSessionId().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Test
    void 회차별_예약자_목록_잘못된_입력의_원문을_로그에_남기지_않는다(CapturedOutput output) throws Exception {
        Fixture fixture = createFixture();
        String sensitiveSessionId = "someone@example.com";

        performGet(fixture.owner(), fixture.content().getContentId(), sensitiveSessionId)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(output.getOut())
            .contains(
                "Session reservation list read. requestId=",
                "contentId=" + fixture.content().getContentId()
                    + ", sessionId=null, resultCount=0, resultCode=INVALID_INPUT"
            )
            .doesNotContain(sensitiveSessionId);
    }

    @Test
    void 회차별_예약자_목록_정합성_오류면_INTERNAL_SERVER_ERROR_결과를_로그에_남긴다(
        CapturedOutput output
    ) throws Exception {
        Fixture fixture = createFixture();
        saveReservation(
            fixture,
            null,
            ReservationStatus.CONFIRMED,
            FIRST_CONFIRMED_AT,
            "active-hold",
            CapacityHoldStatus.ACTIVE
        );
        entityManager.flush();
        entityManager.clear();

        performGet(fixture.owner(), fixture.content().getContentId(), fixture.session().getSessionId().toString())
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        assertThat(output.getOut()).contains(
            "Session reservation list read. requestId=",
            "contentId=" + fixture.content().getContentId()
                + ", sessionId=" + fixture.session().getSessionId()
                + ", resultCount=0, resultCode=INTERNAL_SERVER_ERROR"
        );
    }

    @Test
    void 회차별_예약자_목록_인증되지_않으면_UNAUTHENTICATED를_반환한다() throws Exception {
        Fixture fixture = createFixture();

        mockMvc.perform(get("/api/v1/operator/contents/{contentId}/reservations", fixture.content().getContentId())
            .param("sessionId", fixture.session().getSessionId().toString()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private ResultActions performGet(AppUser user, Long contentId, String sessionId) throws Exception {
        return mockMvc.perform(get("/api/v1/operator/contents/{contentId}/reservations", contentId)
            .param("sessionId", sessionId)
            .header(HttpHeaders.AUTHORIZATION, bearerToken(user)));
    }

    private Fixture createFixture() {
        return createFixture(ContentSessionStatus.SCHEDULED);
    }

    private Fixture createFixture(ContentSessionStatus sessionStatus) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("G" + suffix, "김해시", true));
        AppUser owner = saveOperator(region, "소유 운영자");
        Content content = saveContent(region, owner, "김해 문화 체험");
        ContentSession session = saveSession(content, region, owner, sessionStatus);
        return new Fixture(region, owner, content, session);
    }

    private AppUser saveOperator(Region region, String name) {
        AppUser operator = saveUser(name, "010-3000-" + randomPhoneSuffix());
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        return operator;
    }

    private AppUser saveWithdrawingOperator(Region region, String name) {
        AppUser operator = saveUser(name, "010-4000-" + randomPhoneSuffix(), AppUserStatus.WITHDRAWING);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        return operator;
    }

    private AppUser saveUser(String name, String phone) {
        return saveUser(name, phone, AppUserStatus.ACTIVE);
    }

    private AppUser saveUser(String name, String phone, AppUserStatus status) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            "user-" + suffix + "@example.com",
            "hashed-password",
            name,
            phone,
            status
        ));
    }

    private Content saveContent(Region region, AppUser owner, String title) {
        return contentRepository.saveAndFlush(new Content(
            region,
            owner,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            title,
            "콘텐츠 설명",
            "김해시",
            "10:00~18:00",
            "055-000-0000",
            "주의사항",
            "만 7세 이상",
            "준비물 없음",
            "취소 정책",
            FIRST_CONFIRMED_AT
        ));
    }

    private ContentSession saveScheduledSession(Content content, Region region, AppUser owner) {
        return saveSession(content, region, owner, ContentSessionStatus.SCHEDULED);
    }

    private ContentSession saveSession(
        Content content,
        Region region,
        AppUser owner,
        ContentSessionStatus sessionStatus
    ) {
        ContentSession session = new ContentSession(
            content,
            region,
            SESSION_STARTS_AT,
            SESSION_ENDS_AT,
            CHECKIN_OPEN_AT,
            CHECKIN_CLOSE_AT,
            20
        );
        if (sessionStatus == ContentSessionStatus.SCHEDULED) {
            session.approve(owner, FIRST_CONFIRMED_AT);
        } else if (sessionStatus == ContentSessionStatus.REJECTED) {
            session.reject(owner, FIRST_CONFIRMED_AT, "반려 사유");
        } else if (sessionStatus != ContentSessionStatus.PENDING) {
            throw new IllegalArgumentException("unsupported session status");
        }
        return contentSessionRepository.saveAndFlush(session);
    }

    private Reservation saveReservation(
        Fixture fixture,
        AppUser participant,
        ReservationStatus status,
        Instant confirmedAt,
        String label
    ) {
        return saveReservation(
            fixture,
            participant,
            status,
            confirmedAt,
            label,
            CapacityHoldStatus.CONSUMED
        );
    }

    private Reservation saveReservation(
        Fixture fixture,
        AppUser participant,
        ReservationStatus status,
        Instant confirmedAt,
        String label,
        CapacityHoldStatus capacityHoldStatus
    ) {
        Instant holdTerminalAt = capacityHoldStatus == CapacityHoldStatus.CONSUMED
            ? confirmedAt.plusSeconds(1)
            : null;
        CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            fixture.region(),
            fixture.session(),
            participant,
            HELD_QUANTITY,
            capacityHoldStatus,
            confirmedAt.plusSeconds(600),
            holdTerminalAt,
            null,
            null,
            confirmedAt
        ));
        Instant cancelledAt = status == ReservationStatus.CANCELLED ? confirmedAt.plusSeconds(60) : null;
        String cancellationReason = status == ReservationStatus.CANCELLED ? "운영자 취소" : null;
        Instant expiredAt = status == ReservationStatus.EXPIRED ? confirmedAt.plusSeconds(60) : null;
        return reservationRepository.saveAndFlush(new Reservation(
            "R-" + label + '-' + UUID.randomUUID(),
            "qr-" + label,
            fixture.region(),
            hold,
            fixture.session(),
            participant,
            status,
            confirmedAt,
            cancelledAt,
            cancellationReason,
            expiredAt,
            null
        ));
    }

    private void assertReadDoesNotChangeState(Fixture fixture, List<Reservation> reservations) {
        entityManager.flush();
        entityManager.clear();

        assertThat(contentSessionRepository.findById(fixture.session().getSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(20));
        assertThat(reservations).allSatisfy(reservation -> assertThat(
            reservationRepository.findById(reservation.getReservationId())
        ).hasValueSatisfying(current -> {
            assertThat(current.getStatus()).isEqualTo(reservation.getStatus());
            assertThat(current.getConfirmedAt()).isEqualTo(reservation.getConfirmedAt());
        }));
        assertThat(visitRepository.count()).isEqualTo(1);
        assertThat(auditEventRepository.findAll()).isEmpty();
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private String randomPhoneSuffix() {
        return String.format("%04d", Math.floorMod(System.nanoTime(), 10_000));
    }

    private record Fixture(
        Region region,
        AppUser owner,
        Content content,
        ContentSession session
    ) {
    }
}
