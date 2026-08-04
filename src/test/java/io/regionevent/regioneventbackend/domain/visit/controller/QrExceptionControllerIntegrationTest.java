package io.regionevent.regioneventbackend.domain.visit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QrExceptionControllerIntegrationTest {

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

    @Autowired
    QrExceptionControllerIntegrationTest(
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
        JwtAccessTokenService jwtAccessTokenService
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
    }

    @Test
    void getQrExceptions_정상_조회하면_허용된_사유만_최신순으로_반환한다() throws Exception {
        Fixture fixture = createFixture();
        long reservationCount = reservationRepository.count();
        long visitCount = visitRepository.count();
        long capacityHoldCount = capacityHoldRepository.count();
        long auditEventCount = auditEventRepository.count();

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions")
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("QR 예외 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.exceptions.length()").value(3))
            .andExpect(jsonPath("$.data.exceptions[0].exceptionId").value(fixture.qrFailureEvent().toString()))
            .andExpect(jsonPath("$.data.exceptions[0].exceptionType").value("QR_CHECK_IN_FAILURE"))
            .andExpect(jsonPath("$.data.exceptions[0].result").value("FAILURE"))
            .andExpect(jsonPath("$.data.exceptions[0].reasonCode").value("QR_CHECK_IN_SIGNATURE_INVALID"))
            .andExpect(jsonPath("$.data.exceptions[0].reservationResolved").value(false))
            .andExpect(jsonPath("$.data.exceptions[0].reservationId").isEmpty())
            .andExpect(jsonPath("$.data.exceptions[0].contentId").isEmpty())
            .andExpect(jsonPath("$.data.exceptions[0].sessionId").isEmpty())
            .andExpect(jsonPath("$.data.exceptions[0].occurredAt").value(endsWith("Z")))
            .andExpect(jsonPath("$.data.exceptions[1].exceptionId").value(fixture.lookupEvent().toString()))
            .andExpect(jsonPath("$.data.exceptions[1].exceptionType").value("RESERVATION_NUMBER_LOOKUP"))
            .andExpect(jsonPath("$.data.exceptions[1].reservationResolved").value(true))
            .andExpect(jsonPath("$.data.exceptions[1].reservationId")
                .value(fixture.reservation().getReservationId().toString()))
            .andExpect(jsonPath("$.data.exceptions[1].contentId").value(fixture.content().getContentId().toString()))
            .andExpect(jsonPath("$.data.exceptions[1].sessionId").value(fixture.session().getSessionId().toString()))
            .andExpect(jsonPath("$.data.exceptions[2].exceptionId").value(fixture.manualEvent().toString()))
            .andExpect(jsonPath("$.data.exceptions[2].exceptionType").value("MANUAL_CHECK_IN"))
            .andExpect(jsonPath("$.data.exceptions[*].reasonCode").value(not(hasItem("CONTENT_APPROVED"))))
            .andExpect(jsonPath("$.data.nextCursor").isEmpty())
            .andExpect(jsonPath("$.data.hasNext").value(false));

        assertThat(reservationRepository.count()).isEqualTo(reservationCount);
        assertThat(visitRepository.count()).isEqualTo(visitCount);
        assertThat(capacityHoldRepository.count()).isEqualTo(capacityHoldCount);
        assertThat(auditEventRepository.count()).isEqualTo(auditEventCount);
    }

    @Test
    void getQrExceptions_커서로_다음_페이지를_조회하면_중복을_반환하지_않는다() throws Exception {
        Fixture fixture = createFixture();

        MvcResult firstPage = mockMvc.perform(get("/api/v1/region-admin/qr-exceptions")
                .queryParam("size", "2")
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.exceptions[*].exceptionId").value(contains(
                fixture.qrFailureEvent().toString(),
                fixture.lookupEvent().toString()
            )))
            .andExpect(jsonPath("$.data.hasNext").value(true))
            .andExpect(jsonPath("$.data.nextCursor").value(matchesPattern(".+")))
            .andReturn();
        String nextCursor = JsonPath.read(
            firstPage.getResponse().getContentAsString(),
            "$.data.nextCursor"
        );

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions")
                .queryParam("cursor", nextCursor)
                .queryParam("size", "2")
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.exceptions.length()").value(1))
            .andExpect(jsonPath("$.data.exceptions[0].exceptionId").value(fixture.manualEvent().toString()))
            .andExpect(jsonPath("$.data.exceptions[*].exceptionId").value(not(hasItem(
                fixture.lookupEvent().toString()
            ))))
            .andExpect(jsonPath("$.data.nextCursor").isEmpty())
            .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void getQrExceptions_결과가_없으면_빈_목록을_반환한다() throws Exception {
        Fixture fixture = createFixtureWithoutEvents();

        mockMvc.perform(get("/region-admin/qr-exceptions")
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.exceptions.length()").value(0))
            .andExpect(jsonPath("$.data.nextCursor").isEmpty())
            .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void getQrExceptions_인증이_없으면_UNAUTHENTICATED를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void getQrExceptions_REGION_ADMIN이_아니면_FORBIDDEN을_반환한다() throws Exception {
        Fixture fixture = createFixture();

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions")
                .header("Authorization", bearerToken(fixture.visitor())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getQrExceptions_비활성_REGION_ADMIN이면_FORBIDDEN을_반환한다() throws Exception {
        Fixture fixture = createFixture();

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions")
                .header("Authorization", bearerToken(fixture.inactiveRegionAdmin())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getQrExceptions_size가_범위를_벗어나면_INVALID_INPUT을_반환한다() throws Exception {
        Fixture fixture = createFixtureWithoutEvents();

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions")
                .queryParam("size", "0")
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions")
                .queryParam("size", "101")
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void getQrExceptions_size가_숫자가_아니면_INVALID_TYPE을_반환한다() throws Exception {
        Fixture fixture = createFixtureWithoutEvents();

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions")
                .queryParam("size", "abc")
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void getQrExceptions_cursor가_비어_있으면_INVALID_INPUT을_반환한다() throws Exception {
        Fixture fixture = createFixtureWithoutEvents();

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions")
                .queryParam("cursor", " ")
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void getQrExceptions_malformedCursor_returns_INVALID_INPUT() throws Exception {
        Fixture fixture = createFixtureWithoutEvents();

        mockMvc.perform(get("/api/v1/region-admin/qr-exceptions")
                .queryParam("cursor", "malformed-cursor")
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void getQrExceptions_응답에_개인정보와_예약번호_QR_참조를_포함하지_않는다() throws Exception {
        Fixture fixture = createFixture();

        MvcResult result = mockMvc.perform(get("/api/v1/region-admin/qr-exceptions")
                .header("Authorization", bearerToken(fixture.regionAdmin())))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
            .doesNotContain("개인정보대상")
            .doesNotContain("010-5555-9999")
            .doesNotContain(fixture.reservation().getReservationNo())
            .doesNotContain(fixture.reservation().getQrReference())
            .doesNotContain("\"userId\"");
    }

    private Fixture createFixture() {
        Fixture fixture = createFixtureWithoutEvents();
        Instant now = Instant.now();
        Long qrFailureEvent = saveAuditEvent(
            fixture.region(),
            AuditEventTargetType.RESERVATION,
            null,
            AuditEventResult.FAILURE,
            "QR_CHECK_IN_SIGNATURE_INVALID",
            now.minusSeconds(10)
        );
        Long lookupEvent = saveAuditEvent(
            fixture.region(),
            AuditEventTargetType.RESERVATION,
            fixture.reservation().getReservationId(),
            AuditEventResult.SUCCESS,
            "QR_VERIFICATION_FAILED",
            now.minusSeconds(20)
        );
        Long manualEvent = saveAuditEvent(
            fixture.region(),
            AuditEventTargetType.VISIT,
            fixture.visit().getVisitId(),
            AuditEventResult.SUCCESS,
            "MANUAL_CHECK_IN_QR_SCAN_FAILED_SUCCESS",
            now.minusSeconds(30)
        );
        saveAuditEvent(
            fixture.region(),
            AuditEventTargetType.RESERVATION,
            fixture.reservation().getReservationId(),
            AuditEventResult.SUCCESS,
            "MANUAL_CHECK_IN_QR_SCAN_FAILED_SUCCESS",
            now.minus(Duration.ofDays(91))
        );
        saveAuditEvent(
            fixture.otherRegion(),
            AuditEventTargetType.RESERVATION,
            fixture.reservation().getReservationId(),
            AuditEventResult.FAILURE,
            "QR_CHECK_IN_SESSION_MISMATCH",
            now.minusSeconds(5)
        );
        saveAuditEvent(
            fixture.region(),
            AuditEventTargetType.CONTENT,
            fixture.content().getContentId(),
            AuditEventResult.SUCCESS,
            "CONTENT_APPROVED",
            now.minusSeconds(1)
        );
        return fixture.withEvents(qrFailureEvent, lookupEvent, manualEvent);
    }

    private Fixture createFixtureWithoutEvents() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        Region region = regionRepository.saveAndFlush(new Region("Q" + suffix, "QR 예외 지역", true));
        Region otherRegion = regionRepository.saveAndFlush(new Region("O" + suffix, "타 지역", true));
        AppUser regionAdmin = saveUser("qr-region-admin-" + suffix, AppUserStatus.ACTIVE);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(regionAdmin, UserRole.REGION_ADMIN, region));
        AppUser inactiveRegionAdmin = saveUser("qr-inactive-admin-" + suffix, AppUserStatus.WITHDRAWING);
        userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(inactiveRegionAdmin, UserRole.REGION_ADMIN, region)
        );
        AppUser operator = saveUser("qr-operator-" + suffix, AppUserStatus.ACTIVE);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        AppUser visitor = appUserRepository.saveAndFlush(new AppUser(
            "qr-visitor-" + suffix + "@example.com",
            "hashed-password",
            "개인정보대상",
            "010-5555-9999",
            AppUserStatus.ACTIVE
        ));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(visitor, UserRole.VISITOR, null));
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "QR 예외 테스트 콘텐츠",
            "QR 예외 테스트 설명",
            "김해시",
            "10:00-18:00",
            "055-123-4567",
            "안내사항",
            "전 연령",
            "준비물 없음",
            "취소 정책",
            now.minusSeconds(600)
        ));
        ContentSession session = new ContentSession(
            content,
            region,
            now.minusSeconds(600),
            now.plusSeconds(3_600),
            now.minusSeconds(300),
            now.plusSeconds(1_800),
            10
        );
        session.approve(operator, now.minusSeconds(300));
        session = contentSessionRepository.saveAndFlush(session);
        CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            visitor,
            1,
            CapacityHoldStatus.CONSUMED,
            now,
            now,
            null,
            null
        ));
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "QR-EX-" + suffix,
            "qr-reference-" + suffix,
            region,
            hold,
            session,
            visitor,
            ReservationStatus.CHECKED_IN,
            now,
            null,
            null,
            null,
            null
        ));
        Visit visit = visitRepository.saveAndFlush(new Visit(
            region,
            reservation,
            visitor,
            content,
            session,
            operator,
            CheckinMethod.RESERVATION_NUMBER,
            now
        ));
        return new Fixture(
            region,
            otherRegion,
            regionAdmin,
            inactiveRegionAdmin,
            visitor,
            content,
            session,
            reservation,
            visit,
            null,
            null,
            null
        );
    }

    private Long saveAuditEvent(
        Region region,
        AuditEventTargetType targetType,
        Long targetId,
        AuditEventResult result,
        String reasonCode,
        Instant occurredAt
    ) {
        AuditEvent auditEvent = auditEventRepository.saveAndFlush(new AuditEvent(
            UUID.randomUUID().toString(),
            region,
            targetType,
            targetId,
            null,
            result == AuditEventResult.SUCCESS ? "CHECKED_IN" : null,
            result,
            reasonCode,
            "USER",
            "REGION_ADMIN",
            occurredAt
        ));
        return auditEvent.getAuditEventId();
    }

    private AppUser saveUser(
        String loginIdentifier,
        AppUserStatus status
    ) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier + "@example.com",
            "hashed-password",
            "테스트 사용자",
            "010-1234-5678",
            status
        ));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private record Fixture(
        Region region,
        Region otherRegion,
        AppUser regionAdmin,
        AppUser inactiveRegionAdmin,
        AppUser visitor,
        Content content,
        ContentSession session,
        Reservation reservation,
        Visit visit,
        Long qrFailureEvent,
        Long lookupEvent,
        Long manualEvent
    ) {

        private Fixture withEvents(
            Long qrFailureEvent,
            Long lookupEvent,
            Long manualEvent
        ) {
            return new Fixture(
                region,
                otherRegion,
                regionAdmin,
                inactiveRegionAdmin,
                visitor,
                content,
                session,
                reservation,
                visit,
                qrFailureEvent,
                lookupEvent,
                manualEvent
            );
        }
    }

}
