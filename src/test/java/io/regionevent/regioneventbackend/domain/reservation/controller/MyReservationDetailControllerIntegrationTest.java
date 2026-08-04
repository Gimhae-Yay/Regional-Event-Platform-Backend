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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

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
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class MyReservationDetailControllerIntegrationTest {

    private static final Instant SESSION_STARTS_AT = Instant.parse("2030-08-10T01:00:00Z");
    private static final Instant SESSION_ENDS_AT = Instant.parse("2030-08-10T03:00:00Z");
    private static final Instant CHECKIN_OPEN_AT = Instant.parse("2030-08-10T00:30:00Z");
    private static final Instant CHECKIN_CLOSE_AT = Instant.parse("2030-08-10T01:30:00Z");
    private static final Instant CONFIRMED_AT = Instant.parse("2030-08-01T01:00:00Z");
    private static final Instant CANCELLED_AT = Instant.parse("2030-08-02T01:00:00Z");
    private static final Instant EXPIRED_AT = Instant.parse("2030-08-03T01:00:00Z");
    private static final Instant CHECKED_AT = Instant.parse("2030-08-04T01:00:00Z");

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final VisitRepository visitRepository;
    private final AuditEventRepository auditEventRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;

    @Autowired
    MyReservationDetailControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        VisitRepository visitRepository,
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
        this.visitRepository = visitRepository;
        this.auditEventRepository = auditEventRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.entityManager = entityManager;
    }

    @Test
    void 예약_상세_조회_모든_예약_상태의_계약_필드와_체크인_정보를_반환하고_상태를_변경하지_않는다(
        CapturedOutput output
    ) throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE);
        Reservation confirmed = saveReservation(fixture, ReservationStatus.CONFIRMED, "confirmed");
        Reservation checkedIn = saveReservation(fixture, ReservationStatus.CHECKED_IN, "checked-in");
        Reservation cancelled = saveReservation(fixture, ReservationStatus.CANCELLED, "cancelled");
        Reservation expired = saveReservation(fixture, ReservationStatus.EXPIRED, "expired");
        Visit visit = visitRepository.saveAndFlush(new Visit(
            fixture.region(),
            checkedIn,
            fixture.user(),
            fixture.content(),
            fixture.session(),
            fixture.operator(),
            CheckinMethod.QR,
            CHECKED_AT
        ));
        entityManager.flush();
        entityManager.clear();

        ResultActions confirmedResult = performGet(fixture.user(), confirmed.getReservationId());
        confirmedResult.andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("예약 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.reservation.reservationId").value(confirmed.getReservationId().toString()))
            .andExpect(jsonPath("$.data.reservation.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.data.reservation.confirmedAt").value("2030-08-01T01:00:00Z"))
            .andExpect(jsonPath("$.data.reservation.cancelledAt").doesNotExist())
            .andExpect(jsonPath("$.data.reservation.cancellationReason").doesNotExist())
            .andExpect(jsonPath("$.data.reservation.expiredAt").doesNotExist())
            .andExpect(jsonPath("$.data.session.sessionId").value(fixture.session().getSessionId().toString()))
            .andExpect(jsonPath("$.data.session.contentId").value(fixture.content().getContentId().toString()))
            .andExpect(jsonPath("$.data.session.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.data.session.startsAt").value("2030-08-10T10:00:00+09:00"))
            .andExpect(jsonPath("$.data.session.endsAt").value("2030-08-10T12:00:00+09:00"))
            .andExpect(jsonPath("$.data.session.checkinOpenAt").value("2030-08-10T09:30:00+09:00"))
            .andExpect(jsonPath("$.data.session.checkinCloseAt").value("2030-08-10T10:30:00+09:00"))
            .andExpect(jsonPath("$.data.checkIn.checkedIn").value(false))
            .andExpect(jsonPath("$.data.checkIn.checkedAt").doesNotExist());

        performGet(fixture.user(), checkedIn.getReservationId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reservation.status").value("CHECKED_IN"))
            .andExpect(jsonPath("$.data.checkIn.checkedIn").value(true))
            .andExpect(jsonPath("$.data.checkIn.checkedAt").value("2030-08-04T01:00:00Z"));
        performGet(fixture.user(), cancelled.getReservationId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reservation.status").value("CANCELLED"))
            .andExpect(jsonPath("$.data.reservation.cancelledAt").value("2030-08-02T01:00:00Z"))
            .andExpect(jsonPath("$.data.reservation.cancellationReason").value("방문자 요청"))
            .andExpect(jsonPath("$.data.checkIn.checkedIn").value(false));
        performGet(fixture.user(), expired.getReservationId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reservation.status").value("EXPIRED"))
            .andExpect(jsonPath("$.data.reservation.expiredAt").value("2030-08-03T01:00:00Z"))
            .andExpect(jsonPath("$.data.checkIn.checkedIn").value(false));

        String responseBody = confirmedResult.andReturn().getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain(
            fixture.user().getName(),
            fixture.user().getPhone(),
            confirmed.getQrReference(),
            "qrReference",
            "userId"
        );
        assertThat(output.getOut()).contains(
            "Reservation detail read. requestId=",
            "reservationId=" + checkedIn.getReservationId()
                + ", sessionId=" + fixture.session().getSessionId()
                + ", visitId=" + visit.getVisitId()
                + ", resultCode=SUCCESS"
        ).doesNotContain(
            fixture.user().getName(),
            fixture.user().getPhone(),
            checkedIn.getQrReference()
        );
        assertReadDoesNotChangeState(fixture, List.of(confirmed, checkedIn, cancelled, expired), 1);
    }

    @Test
    void 예약_상세_조회_활성_회원이_아니거나_다른_회원의_예약이면_FORBIDDEN과_무변경을_반환한다()
        throws Exception {
        Fixture withdrawingFixture = createFixture(AppUserStatus.WITHDRAWING);
        Reservation withdrawingReservation = saveReservation(
            withdrawingFixture,
            ReservationStatus.CONFIRMED,
            "withdrawing"
        );
        Fixture activeFixture = createFixture(AppUserStatus.ACTIVE);
        Reservation activeReservation = saveReservation(activeFixture, ReservationStatus.CONFIRMED, "owned");
        AppUser anotherUser = saveUser(AppUserStatus.ACTIVE, "다른 방문자", "010-2000-0001");

        performGet(withdrawingFixture.user(), withdrawingReservation.getReservationId())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        performGet(anotherUser, activeReservation.getReservationId())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertReadDoesNotChangeState(withdrawingFixture, List.of(withdrawingReservation), 0);
        assertReadDoesNotChangeState(activeFixture, List.of(activeReservation), 0);
    }

    @Test
    void 예약_상세_조회_입력_대상_부재_미인증의_공통_오류_계약과_비식별_로그를_반환한다(
        CapturedOutput output
    ) throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE);
        String sensitiveReservationId = "someone@example.com";

        performGet(fixture.user(), 0L)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/me/reservations/{reservationId}", sensitiveReservationId)
                .header("Authorization", bearerToken(fixture.user())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        performGet(fixture.user(), 999_999L)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/me/reservations/{reservationId}", sensitiveReservationId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(output.getOut()).contains(
            "Reservation detail read. requestId=",
            "reservationId=null, sessionId=null, visitId=null, resultCode=INVALID_TYPE",
            "reservationId=null, sessionId=null, visitId=null, resultCode=UNAUTHENTICATED",
            "HTTP request completed. method=GET, uri=/api/v1/me/reservations/{reservationId}, status=401"
        ).doesNotContain(sensitiveReservationId);
        assertThat(auditEventRepository.findAll()).isEmpty();
    }

    @Test
    void 예약_상세_조회_체크인_방문_연결이_없으면_INTERNAL_SERVER_ERROR를_로그에_남기고_상태를_변경하지_않는다(
        CapturedOutput output
    ) throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE);
        Reservation reservation = saveReservation(fixture, ReservationStatus.CHECKED_IN, "inconsistent");
        entityManager.flush();
        entityManager.clear();

        performGet(fixture.user(), reservation.getReservationId())
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        assertThat(output.getOut()).contains(
            "Reservation detail read. requestId=",
            "reservationId=" + reservation.getReservationId()
                + ", sessionId=null, visitId=null, resultCode=INTERNAL_SERVER_ERROR"
        );
        assertReadDoesNotChangeState(fixture, List.of(reservation), 0);
    }

    private ResultActions performGet(AppUser user, Long reservationId) throws Exception {
        return mockMvc.perform(get("/api/v1/me/reservations/{reservationId}", reservationId)
            .header("Authorization", bearerToken(user)));
    }

    private Fixture createFixture(AppUserStatus userStatus) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("G" + suffix, "김해시", true));
        AppUser operator = saveUser(AppUserStatus.ACTIVE, "운영자", "010-3000-0001");
        AppUser user = saveUser(userStatus, "김민수", "010-1000-0001");
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 문화 체험",
            "콘텐츠 설명",
            "김해시",
            "10:00~18:00",
            "055-000-0000",
            "주의사항",
            "만 7세 이상",
            "준비물 없음",
            "취소 정책",
            CONFIRMED_AT
        ));
        ContentSession session = new ContentSession(
            content,
            region,
            SESSION_STARTS_AT,
            SESSION_ENDS_AT,
            CHECKIN_OPEN_AT,
            CHECKIN_CLOSE_AT,
            20
        );
        session.approve(operator, CONFIRMED_AT);
        return new Fixture(
            region,
            operator,
            user,
            content,
            contentSessionRepository.saveAndFlush(session)
        );
    }

    private Reservation saveReservation(
        Fixture fixture,
        ReservationStatus status,
        String label
    ) {
        CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            fixture.region(),
            fixture.session(),
            fixture.user(),
            1,
            CapacityHoldStatus.CONSUMED,
            SESSION_STARTS_AT,
            CONFIRMED_AT,
            null,
            null,
            CONFIRMED_AT
        ));
        Instant cancelledAt = status == ReservationStatus.CANCELLED ? CANCELLED_AT : null;
        String cancellationReason = status == ReservationStatus.CANCELLED ? "방문자 요청" : null;
        Instant expiredAt = status == ReservationStatus.EXPIRED ? EXPIRED_AT : null;
        return reservationRepository.saveAndFlush(new Reservation(
            "R-" + label + '-' + UUID.randomUUID(),
            "qr-" + label + '-' + UUID.randomUUID(),
            fixture.region(),
            hold,
            fixture.session(),
            fixture.user(),
            status,
            CONFIRMED_AT,
            cancelledAt,
            cancellationReason,
            expiredAt,
            null
        ));
    }

    private AppUser saveUser(AppUserStatus status, String name, String phone) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            "user-" + suffix + "@example.com",
            "hashed-password",
            name,
            phone,
            status
        ));
    }

    private void assertReadDoesNotChangeState(
        Fixture fixture,
        List<Reservation> reservations,
        long expectedVisitCount
    ) {
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
        assertThat(visitRepository.count()).isEqualTo(expectedVisitCount);
        assertThat(auditEventRepository.findAll()).isEmpty();
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private record Fixture(
        Region region,
        AppUser operator,
        AppUser user,
        Content content,
        ContentSession session
    ) {
    }
}
