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
class MyReservationListControllerIntegrationTest {

    private static final Instant SESSION_STARTS_AT = Instant.parse("2030-08-10T01:00:00Z");
    private static final Instant SESSION_ENDS_AT = Instant.parse("2030-08-10T03:00:00Z");
    private static final Instant CHECKIN_OPEN_AT = Instant.parse("2030-08-10T00:30:00Z");
    private static final Instant CHECKIN_CLOSE_AT = Instant.parse("2030-08-10T01:30:00Z");
    private static final Instant EARLIER_CONFIRMED_AT = Instant.parse("2030-08-01T01:00:00Z");
    private static final Instant TIED_CONFIRMED_AT = Instant.parse("2030-08-02T01:00:00Z");
    private static final Instant CHECKED_AT = Instant.parse("2030-08-02T01:05:00Z");

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
    MyReservationListControllerIntegrationTest(
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
    void 내_예약_목록은_모든_상태를_확정_시각과_식별자_내림차순으로_반환하고_상태를_변경하지_않는다(
        CapturedOutput output
    ) throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE);
        Reservation confirmed = saveReservation(
            fixture,
            fixture.user(),
            ReservationStatus.CONFIRMED,
            EARLIER_CONFIRMED_AT,
            "confirmed"
        );
        Reservation checkedIn = saveReservation(
            fixture,
            fixture.user(),
            ReservationStatus.CHECKED_IN,
            TIED_CONFIRMED_AT,
            "checked-in"
        );
        Reservation cancelled = saveReservation(
            fixture,
            fixture.user(),
            ReservationStatus.CANCELLED,
            TIED_CONFIRMED_AT,
            "cancelled"
        );
        Reservation expired = saveReservation(
            fixture,
            fixture.user(),
            ReservationStatus.EXPIRED,
            TIED_CONFIRMED_AT,
            "expired"
        );
        AppUser otherUser = saveUser(AppUserStatus.ACTIVE, "다른 방문자", "010-2000-0001");
        Reservation otherUserReservation = saveReservation(
            fixture,
            otherUser,
            ReservationStatus.CONFIRMED,
            TIED_CONFIRMED_AT.plusSeconds(1),
            "other-user"
        );
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

        ResultActions result = performGet(fixture.user());

        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 예약 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.reservations.length()").value(4))
            .andExpect(jsonPath("$.data.reservations[0].reservationId").value(
                expired.getReservationId().toString()
            ))
            .andExpect(jsonPath("$.data.reservations[0].status").value("EXPIRED"))
            .andExpect(jsonPath("$.data.reservations[0].quantity").value(1))
            .andExpect(jsonPath("$.data.reservations[0].checkIn.visitId").doesNotExist())
            .andExpect(jsonPath("$.data.reservations[1].reservationId").value(
                cancelled.getReservationId().toString()
            ))
            .andExpect(jsonPath("$.data.reservations[1].status").value("CANCELLED"))
            .andExpect(jsonPath("$.data.reservations[1].quantity").value(1))
            .andExpect(jsonPath("$.data.reservations[1].checkIn.visitId").doesNotExist())
            .andExpect(jsonPath("$.data.reservations[2].reservationId").value(
                checkedIn.getReservationId().toString()
            ))
            .andExpect(jsonPath("$.data.reservations[2].reservationNo").value(checkedIn.getReservationNo()))
            .andExpect(jsonPath("$.data.reservations[2].status").value("CHECKED_IN"))
            .andExpect(jsonPath("$.data.reservations[2].quantity").value(1))
            .andExpect(jsonPath("$.data.reservations[2].confirmedAt").value("2030-08-02T01:00:00Z"))
            .andExpect(jsonPath("$.data.reservations[2].content.contentId").value(
                fixture.content().getContentId().toString()
            ))
            .andExpect(jsonPath("$.data.reservations[2].content.title").value("김해 문화 체험"))
            .andExpect(jsonPath("$.data.reservations[2].session.sessionId").value(
                fixture.session().getSessionId().toString()
            ))
            .andExpect(jsonPath("$.data.reservations[2].session.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.data.reservations[2].session.startsAt").value(
                "2030-08-10T10:00:00+09:00"
            ))
            .andExpect(jsonPath("$.data.reservations[2].session.endsAt").value(
                "2030-08-10T12:00:00+09:00"
            ))
            .andExpect(jsonPath("$.data.reservations[2].checkIn.checkedIn").value(true))
            .andExpect(jsonPath("$.data.reservations[2].checkIn.checkedAt").value(
                "2030-08-02T01:05:00Z"
            ))
            .andExpect(jsonPath("$.data.reservations[2].checkIn.visitId").value(
                visit.getVisitId().toString()
            ))
            .andExpect(jsonPath("$.data.reservations[3].reservationId").value(
                confirmed.getReservationId().toString()
            ))
            .andExpect(jsonPath("$.data.reservations[3].status").value("CONFIRMED"))
            .andExpect(jsonPath("$.data.reservations[3].quantity").value(1))
            .andExpect(jsonPath("$.data.reservations[3].checkIn.checkedIn").value(false))
            .andExpect(jsonPath("$.data.reservations[3].checkIn.checkedAt").doesNotExist())
            .andExpect(jsonPath("$.data.reservations[3].checkIn.visitId").doesNotExist());

        String responseBody = result.andReturn().getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain(
            fixture.user().getName(),
            fixture.user().getPhone(),
            otherUser.getName(),
            otherUser.getPhone(),
            checkedIn.getQrReference(),
            otherUserReservation.getReservationNo(),
            "qrReference",
            "userId"
        );
        assertThat(output.getOut()).contains(
            "My reservation list read. requestId=",
            "resultCount=4, resultCode=SUCCESS"
        ).doesNotContain(
            fixture.user().getName(),
            fixture.user().getPhone(),
            checkedIn.getQrReference()
        );
        assertReadDoesNotChangeState(
            fixture,
            List.of(confirmed, checkedIn, cancelled, expired, otherUserReservation),
            List.of(visit)
        );
    }

    @Test
    void 내_예약이_없으면_빈_배열을_반환하고_상태를_변경하지_않는다() throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE);

        performGet(fixture.user())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reservations").isArray())
            .andExpect(jsonPath("$.data.reservations").isEmpty());

        assertReadDoesNotChangeState(fixture, List.of(), List.of());
    }

    @Test
    void 비활성_회원과_미인증_요청은_오류_결과를_로그에_남기고_상태를_변경하지_않는다(
        CapturedOutput output
    ) throws Exception {
        Fixture fixture = createFixture(AppUserStatus.WITHDRAWING);
        Reservation reservation = saveReservation(
            fixture,
            fixture.user(),
            ReservationStatus.CONFIRMED,
            EARLIER_CONFIRMED_AT,
            "withdrawing"
        );

        performGet(fixture.user())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/me/reservations"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(output.getOut()).contains(
            "My reservation list read. requestId=",
            "resultCount=0, resultCode=FORBIDDEN",
            "resultCount=0, resultCode=UNAUTHENTICATED",
            "HTTP request completed. method=GET, uri=/api/v1/me/reservations, status=401"
        );
        assertReadDoesNotChangeState(fixture, List.of(reservation), List.of());
    }

    @Test
    void 체크인_예약에_방문_연결이_없으면_정합성_오류를_반환하고_상태를_변경하지_않는다(
        CapturedOutput output
    ) throws Exception {
        Fixture fixture = createFixture(AppUserStatus.ACTIVE);
        Reservation reservation = saveReservation(
            fixture,
            fixture.user(),
            ReservationStatus.CHECKED_IN,
            EARLIER_CONFIRMED_AT,
            "inconsistent"
        );
        entityManager.flush();
        entityManager.clear();

        performGet(fixture.user())
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        assertThat(output.getOut()).contains(
            "My reservation list read. requestId=",
            "resultCount=0, resultCode=INTERNAL_SERVER_ERROR"
        );
        assertReadDoesNotChangeState(fixture, List.of(reservation), List.of());
    }

    private ResultActions performGet(AppUser user) throws Exception {
        return mockMvc.perform(get("/api/v1/me/reservations")
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
            EARLIER_CONFIRMED_AT
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
        session.approve(operator, EARLIER_CONFIRMED_AT);
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
        AppUser user,
        ReservationStatus status,
        Instant confirmedAt,
        String label
    ) {
        CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            fixture.region(),
            fixture.session(),
            user,
            1,
            CapacityHoldStatus.CONSUMED,
            SESSION_STARTS_AT,
            confirmedAt,
            null,
            null,
            confirmedAt
        ));
        Instant cancelledAt = status == ReservationStatus.CANCELLED
            ? confirmedAt.plusSeconds(60)
            : null;
        String cancellationReason = status == ReservationStatus.CANCELLED ? "방문자 요청" : null;
        Instant expiredAt = status == ReservationStatus.EXPIRED ? confirmedAt.plusSeconds(120) : null;
        return reservationRepository.saveAndFlush(new Reservation(
            "R-" + label + '-' + UUID.randomUUID(),
            "qr-" + label + '-' + UUID.randomUUID(),
            fixture.region(),
            hold,
            fixture.session(),
            user,
            status,
            confirmedAt,
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
        List<Visit> visits
    ) {
        entityManager.flush();
        entityManager.clear();

        assertThat(contentRepository.findById(fixture.content().getContentId()))
            .hasValueSatisfying(content -> {
                assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
                assertThat(content.getTitle()).isEqualTo("김해 문화 체험");
            });
        assertThat(contentSessionRepository.findById(fixture.session().getSessionId()))
            .hasValueSatisfying(session -> {
                assertThat(session.getRemainingCapacity()).isEqualTo(20);
                assertThat(session.getStartsAt()).isEqualTo(SESSION_STARTS_AT);
                assertThat(session.getEndsAt()).isEqualTo(SESSION_ENDS_AT);
            });
        assertThat(capacityHoldRepository.findAll())
            .allSatisfy(hold -> assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.CONSUMED));
        assertThat(reservations).allSatisfy(reservation -> assertThat(
            reservationRepository.findById(reservation.getReservationId())
        ).hasValueSatisfying(current -> {
            assertThat(current.getStatus()).isEqualTo(reservation.getStatus());
            assertThat(current.getConfirmedAt()).isEqualTo(reservation.getConfirmedAt());
            assertThat(current.getQrReference()).isEqualTo(reservation.getQrReference());
        }));
        assertThat(visitRepository.findAll()).hasSize(visits.size());
        assertThat(visits).allSatisfy(visit -> assertThat(visitRepository.findById(visit.getVisitId()))
            .hasValueSatisfying(current -> assertThat(current.getCheckedAt()).isEqualTo(visit.getCheckedAt())));
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
