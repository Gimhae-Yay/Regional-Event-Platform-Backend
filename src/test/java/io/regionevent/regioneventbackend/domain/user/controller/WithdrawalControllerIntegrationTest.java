package io.regionevent.regioneventbackend.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventActorLink;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyOperation;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecord;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecordStatus;
import io.regionevent.regioneventbackend.domain.idempotency.repository.IdempotencyRecordRepository;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;
import io.regionevent.regioneventbackend.domain.operator.repository.OperatorApplicationRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.review.entity.Review;
import io.regionevent.regioneventbackend.domain.review.entity.ReviewStatus;
import io.regionevent.regioneventbackend.domain.review.repository.ReviewRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStoreUnavailableException;

@SpringBootTest
@AutoConfigureMockMvc
@Import(WithdrawalControllerIntegrationTest.WithdrawalTestConfiguration.class)
@Transactional
class WithdrawalControllerIntegrationTest {

    private static final String WITHDRAWAL_PATH = "/api/v1/auth/delete";
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private ContentSessionRepository contentSessionRepository;

    @Autowired
    private CapacityHoldRepository capacityHoldRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private OperatorApplicationRepository operatorApplicationRepository;

    @Autowired
    private VisitRepository visitRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private AuditEventActorLinkRepository auditEventActorLinkRepository;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @BeforeEach
    void setUp() {
        reset(refreshTokenStore);
    }

    @Test
    void withdraw_withVisitor_removesAccountAndDirectUserLinks() throws Exception {
        WithdrawalFixture fixture = createWithdrawalFixture();

        mockMvc.perform(delete(WITHDRAWAL_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(fixture.user())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("회원탈퇴에 성공했습니다."))
            .andExpect(jsonPath("$.data").isEmpty())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("refreshToken=")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Path=/api/v1/auth")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("HttpOnly")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Secure")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("SameSite=Strict")));

        assertThat(appUserRepository.findById(fixture.user().getUserId())).isEmpty();
        assertThat(userRoleAssignmentRepository.findAllByAppUserUserIdAndStatus(fixture.user().getUserId(), UserRoleAssignmentStatus.ACTIVE)).isEmpty();
        assertThat(userRoleAssignmentRepository.findById(fixture.visitorRoleAssignment().getRoleAssignmentId()))
            .hasValueSatisfying(assignment -> {
                assertThat(assignment.getStatus()).isEqualTo(UserRoleAssignmentStatus.REVOKED);
                assertThat(assignment.getRevokedAt()).isNotNull();
                assertThat(assignment.getRevokeReasonCode()).isEqualTo("USER_WITHDRAWAL");
                assertThat(assignment.getAppUser()).isNull();
            });
        assertThat(userRoleAssignmentRepository.findById(fixture.revokedOperatorRoleAssignment().getRoleAssignmentId()))
            .hasValueSatisfying(assignment -> {
                assertThat(assignment.getStatus()).isEqualTo(UserRoleAssignmentStatus.REVOKED);
                assertThat(assignment.getAppUser()).isNull();
            });
        assertThat(operatorApplicationRepository.findById(fixture.application().getOperatorApplicationId()))
            .hasValueSatisfying(application -> {
                assertThat(application.getStatus()).isEqualTo(OperatorApplicationStatus.CANCELLED);
                assertThat(application.getApplicant()).isNull();
                assertThat(application.getBusinessInformation()).isNull();
            });
        assertThat(capacityHoldRepository.findById(fixture.capacityHold().getHoldId()))
            .hasValueSatisfying(hold -> {
                assertThat(hold.getStatus()).isEqualTo(CapacityHoldStatus.CONSUMED);
                assertThat(hold.getUser()).isNull();
            });
        assertThat(reservationRepository.findById(fixture.reservation().getReservationId()))
            .hasValueSatisfying(reservation -> {
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
                assertThat(reservation.getUser()).isNull();
            });
        assertThat(contentSessionRepository.findById(fixture.contentSession().getSessionId()))
            .hasValueSatisfying(session -> assertThat(session.getRemainingCapacity()).isEqualTo(10));
        assertThat(visitRepository.findById(fixture.visit().getVisitId()))
            .hasValueSatisfying(visit -> {
                assertThat(visit.getUser()).isNull();
                assertThat(visit.getAuthorUnlinkedAt()).isNotNull();
            });
        assertThat(reviewRepository.findById(fixture.review().getReviewId()))
            .hasValueSatisfying(review -> {
                assertThat(review.getUser()).isNull();
                assertThat(review.getAuthorUnlinkedAt()).isNotNull();
            });
        assertThat(idempotencyRecordRepository.findById(fixture.idempotencyRecord().getIdempotencyRecordId()))
            .hasValueSatisfying(record -> {
                assertThat(record.getActor()).isNull();
                assertThat(record.getIdempotencyKeyHash()).isNull();
            });
        assertThat(auditEventActorLinkRepository.findById(fixture.auditEvent().getAuditEventId())).isEmpty();
        verify(refreshTokenStore).revokeAllFamilies(fixture.user().getUserId());
    }

    @Test
    void withdraw_withWithdrawingUser_returnsUnauthenticatedWithoutCookie() throws Exception {
        AppUser user = saveUser("withdrawing@example.com", AppUserStatus.WITHDRAWING);

        mockMvc.perform(delete(WITHDRAWAL_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(user)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(appUserRepository.findById(user.getUserId())).isPresent();
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"OPERATOR", "REGION_ADMIN"})
    void withdraw_withPrivilegedRole_returnsForbiddenWithoutChanges(UserRole role) throws Exception {
        AppUser user = saveUser(role.name().toLowerCase() + "@example.com", AppUserStatus.ACTIVE);
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, role, region));

        mockMvc.perform(delete(WITHDRAWAL_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(user)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(appUserRepository.findById(user.getUserId())).hasValueSatisfying(
            unchanged -> assertThat(unchanged.getStatus()).isEqualTo(AppUserStatus.ACTIVE)
        );
    }

    @Test
    void withdraw_withOwnedContent_returnsForbiddenWithoutChanges() throws Exception {
        AppUser user = saveUser("owner@example.com", AppUserStatus.ACTIVE);
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        contentRepository.saveAndFlush(content(region, user));

        mockMvc.perform(delete(WITHDRAWAL_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(user)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(appUserRepository.findById(user.getUserId())).hasValueSatisfying(
            unchanged -> assertThat(unchanged.getStatus()).isEqualTo(AppUserStatus.ACTIVE)
        );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void withdraw_whenRefreshTokenStoreIsUnavailable_rollsBackUserState() throws Exception {
        AppUser user = saveUser("redis-unavailable@example.com", AppUserStatus.ACTIVE);
        doThrow(new RefreshTokenStoreUnavailableException(new IllegalStateException("Redis unavailable")))
            .when(refreshTokenStore)
            .revokeAllFamilies(user.getUserId());

        mockMvc.perform(delete(WITHDRAWAL_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(user)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("AUTH_SERVICE_UNAVAILABLE"))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(appUserRepository.findById(user.getUserId())).hasValueSatisfying(
            unchanged -> assertThat(unchanged.getStatus()).isEqualTo(AppUserStatus.ACTIVE)
        );
    }

    @Test
    void withdraw_withoutAccessToken_returnsUnauthenticated() throws Exception {
        mockMvc.perform(delete(WITHDRAWAL_PATH))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    private WithdrawalFixture createWithdrawalFixture() {
        AppUser user = saveUser("visitor@example.com", AppUserStatus.ACTIVE);
        AppUser owner = saveUser("owner@example.com", AppUserStatus.ACTIVE);
        AppUser reviewer = saveUser("reviewer@example.com", AppUserStatus.ACTIVE);
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        UserRoleAssignment visitorRoleAssignment = userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(user, UserRole.VISITOR, null)
        );
        UserRoleAssignment revokedOperatorRoleAssignment = new UserRoleAssignment(user, UserRole.OPERATOR, region);
        revokedOperatorRoleAssignment.revoke(NOW, "OPERATOR_REVOCATION");
        revokedOperatorRoleAssignment = userRoleAssignmentRepository.saveAndFlush(revokedOperatorRoleAssignment);

        Content content = contentRepository.saveAndFlush(content(region, owner));
        ContentSession contentSession = new ContentSession(
            content,
            region,
            NOW.plusSeconds(86_400),
            NOW.plusSeconds(90_000),
            NOW.plusSeconds(85_200),
            NOW.plusSeconds(89_000),
            10
        );
        contentSession.approve(reviewer, NOW);
        contentSession = contentSessionRepository.saveAndFlush(contentSession);

        CapacityHold capacityHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            contentSession,
            user,
            1,
            CapacityHoldStatus.CONSUMED,
            NOW.plusSeconds(3_600),
            NOW,
            null,
            null,
            NOW
        ));
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "reservation-no",
            "qr-reference",
            region,
            capacityHold,
            contentSession,
            user,
            ReservationStatus.CHECKED_IN,
            NOW,
            null,
            null,
            null,
            null
        ));
        Visit visit = visitRepository.saveAndFlush(new Visit(
            region,
            reservation,
            user,
            content,
            contentSession,
            reviewer,
            CheckinMethod.QR,
            NOW
        ));
        Review review = reviewRepository.saveAndFlush(new Review(
            region,
            visit,
            user,
            content,
            5,
            "좋은 행사였습니다.",
            ReviewStatus.PUBLISHED,
            null
        ));
        OperatorApplication application = operatorApplicationRepository.saveAndFlush(new OperatorApplication(
            user,
            region,
            "사업자 정보",
            OperatorApplicationStatus.PENDING,
            null,
            null
        ));
        IdempotencyRecord idempotencyRecord = idempotencyRecordRepository.saveAndFlush(new IdempotencyRecord(
            user,
            IdempotencyOperation.CHECK_IN,
            "key-hash",
            "request-hash",
            IdempotencyRecordStatus.PROCESSING,
            null,
            null,
            null,
            NOW,
            null,
            NOW.plusSeconds(86_400)
        ));
        AuditEvent auditEvent = auditEventRepository.saveAndFlush(new AuditEvent(
            "123e4567-e89b-12d3-a456-426614174000",
            region,
            AuditEventTargetType.VISIT,
            visit.getVisitId(),
            null,
            "CHECKED_IN",
            AuditEventResult.SUCCESS,
            null,
            "USER",
            "VISITOR",
            NOW
        ));
        auditEventActorLinkRepository.saveAndFlush(new AuditEventActorLink(auditEvent, user));
        return new WithdrawalFixture(
            user,
            contentSession,
            capacityHold,
            reservation,
            application,
            visit,
            review,
            idempotencyRecord,
            auditEvent,
            visitorRoleAssignment,
            revokedOperatorRoleAssignment
        );
    }

    private Content content(Region region, AppUser owner) {
        return new Content(
            region,
            owner,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 행사",
            "행사 설명",
            "김해시",
            "10:00-18:00",
            "01012345678",
            "주의사항",
            "전체 이용가",
            "준비물 없음",
            "취소 정책",
            NOW
        );
    }

    private AppUser saveUser(String email, AppUserStatus status) {
        return appUserRepository.saveAndFlush(new AppUser(
            email,
            "password-hash",
            "홍길동",
            "01012345678",
            status
        ));
    }

    private String bearerTokenFor(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private record WithdrawalFixture(
        AppUser user,
        ContentSession contentSession,
        CapacityHold capacityHold,
        Reservation reservation,
        OperatorApplication application,
        Visit visit,
        Review review,
        IdempotencyRecord idempotencyRecord,
        AuditEvent auditEvent,
        UserRoleAssignment visitorRoleAssignment,
        UserRoleAssignment revokedOperatorRoleAssignment
    ) {
    }

    @TestConfiguration
    static class WithdrawalTestConfiguration {

        @Bean
        @Primary
        RefreshTokenStore refreshTokenStore() {
            return mock(RefreshTokenStore.class);
        }
    }
}
