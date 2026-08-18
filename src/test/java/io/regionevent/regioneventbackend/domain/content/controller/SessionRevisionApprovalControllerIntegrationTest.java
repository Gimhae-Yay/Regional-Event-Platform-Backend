package io.regionevent.regioneventbackend.domain.content.controller;

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
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.content.repository.SessionRevisionRepository;
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
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionRevisionApprovalControllerIntegrationTest {

    private static final Instant ORIGINAL_STARTS_AT = Instant.parse("2099-08-29T01:00:00Z");
    private static final Instant ORIGINAL_ENDS_AT = Instant.parse("2099-08-29T03:00:00Z");
    private static final Instant ORIGINAL_CHECKIN_OPEN_AT = Instant.parse("2099-08-29T00:30:00Z");
    private static final Instant ORIGINAL_CHECKIN_CLOSE_AT = Instant.parse("2099-08-29T02:30:00Z");
    private static final Instant CANDIDATE_STARTS_AT = Instant.parse("2099-08-30T01:00:00Z");
    private static final Instant CANDIDATE_ENDS_AT = Instant.parse("2099-08-30T03:00:00Z");
    private static final Instant CANDIDATE_CHECKIN_OPEN_AT = Instant.parse("2099-08-30T00:30:00Z");
    private static final Instant CANDIDATE_CHECKIN_CLOSE_AT = Instant.parse("2099-08-30T02:30:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private ContentSessionRepository contentSessionRepository;

    @Autowired
    private SessionRevisionRepository sessionRevisionRepository;

    @Autowired
    private CapacityHoldRepository capacityHoldRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void approve_appliesCandidateAndRecordsAuditEvent() throws Exception {
        Fixture fixture = saveFixture("SUCCESS", ORIGINAL_STARTS_AT);

        mockMvc.perform(post(approvePath(fixture.revisionId()))
                .header(HttpHeaders.AUTHORIZATION, bearerToken(fixture.admin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("회차 수정 요청 승인에 성공했습니다."))
            .andExpect(jsonPath("$.data.revisionId").value(fixture.revisionId().toString()))
            .andExpect(jsonPath("$.data.revisionStatus").value("APPROVED"))
            .andExpect(jsonPath("$.data.contentId").value(fixture.contentId().toString()))
            .andExpect(jsonPath("$.data.targetSessionId").value(fixture.sessionId().toString()))
            .andExpect(jsonPath("$.data.sessionVersion").value(fixture.baseSessionVersion() + 1));

        entityManager.clear();
        SessionRevision revision = sessionRevisionRepository.findById(fixture.revisionId()).orElseThrow();
        ContentSession contentSession = contentSessionRepository.findById(fixture.sessionId()).orElseThrow();
        assertThat(revision.getStatus()).isEqualTo(SessionRevisionStatus.APPROVED);
        assertThat(revision.getReviewedBy()).extracting(AppUser::getUserId).isEqualTo(fixture.admin().getUserId());
        assertThat(contentSession.getStartsAt()).isEqualTo(CANDIDATE_STARTS_AT);
        assertThat(contentSession.getEndsAt()).isEqualTo(CANDIDATE_ENDS_AT);
        assertThat(contentSession.getCheckinOpenAt()).isEqualTo(CANDIDATE_CHECKIN_OPEN_AT);
        assertThat(contentSession.getCheckinCloseAt()).isEqualTo(CANDIDATE_CHECKIN_CLOSE_AT);
        assertThat(contentSession.getCapacity()).isEqualTo(30);
        assertThat(contentSession.getRemainingCapacity()).isEqualTo(30);
        assertThat(contentSession.getVersionNo()).isEqualTo(fixture.baseSessionVersion() + 1);
        assertThat(auditEventRepository.findAll()).anySatisfy(auditEvent -> {
            assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.CONTENT_SESSION);
            assertThat(auditEvent.getTargetId()).isEqualTo(fixture.sessionId());
            assertThat(auditEvent.getPreviousState()).isEqualTo("PENDING");
            assertThat(auditEvent.getNextState()).isEqualTo("APPROVED");
            assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
        });
    }

    @Test
    void approve_requiresAuthenticationRegionAdminAndPositiveRevisionId() throws Exception {
        Fixture fixture = saveFixture("AUTH", ORIGINAL_STARTS_AT);
        AppUser visitor = saveUser("visitor-auth@example.com", AppUserStatus.ACTIVE);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(visitor, UserRole.VISITOR, null));
        Region otherRegion = saveRegion("OTHER");
        AppUser otherAdmin = saveRegionAdmin("other-admin@example.com", otherRegion);

        mockMvc.perform(post(approvePath(fixture.revisionId())))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        for (AppUser unauthorizedUser : new AppUser[]{visitor, otherAdmin}) {
            mockMvc.perform(post(approvePath(fixture.revisionId()))
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(unauthorizedUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        mockMvc.perform(post(approvePath(0L))
                .header(HttpHeaders.AUTHORIZATION, bearerToken(fixture.admin())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void approve_whenStateVersionOrActiveHoldConflicts_rollsBack() throws Exception {
        Fixture activeHoldFixture = saveFixture("HOLD", ORIGINAL_STARTS_AT);
        capacityHoldRepository.saveAndFlush(new CapacityHold(
            activeHoldFixture.region(),
            contentSessionRepository.findById(activeHoldFixture.sessionId()).orElseThrow(),
            activeHoldFixture.operator(),
            1,
            CapacityHoldStatus.ACTIVE,
            ORIGINAL_STARTS_AT,
            null,
            null,
            null,
            SUBMITTED_AT
        ));
        expectSessionStateConflict(activeHoldFixture);

        Fixture versionFixture = saveFixture("VERSION", ORIGINAL_STARTS_AT);
        ContentSession versionTarget = contentSessionRepository.findById(versionFixture.sessionId()).orElseThrow();
        versionTarget.applyRevision(
            ORIGINAL_STARTS_AT.plusSeconds(60),
            ORIGINAL_ENDS_AT.plusSeconds(60),
            ORIGINAL_CHECKIN_OPEN_AT.plusSeconds(60),
            ORIGINAL_CHECKIN_CLOSE_AT.plusSeconds(60),
            20
        );
        contentSessionRepository.saveAndFlush(versionTarget);
        expectSessionStateConflict(versionFixture);

        Fixture reservationFixture = saveFixture("RESERVATION", ORIGINAL_STARTS_AT);
        ContentSession reservationTarget = contentSessionRepository.findById(reservationFixture.sessionId()).orElseThrow();
        CapacityHold consumedHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            reservationFixture.region(),
            reservationTarget,
            reservationFixture.operator(),
            1,
            CapacityHoldStatus.CONSUMED,
            ORIGINAL_STARTS_AT,
            SUBMITTED_AT,
            null,
            null,
            SUBMITTED_AT
        ));
        reservationRepository.saveAndFlush(new Reservation(
            "RESERVATION-" + reservationFixture.revisionId(),
            "QR-" + reservationFixture.revisionId(),
            reservationFixture.region(),
            consumedHold,
            reservationTarget,
            reservationFixture.operator(),
            ReservationStatus.CONFIRMED,
            SUBMITTED_AT,
            null,
            null,
            null,
            null
        ));
        expectSessionStateConflict(reservationFixture);

        Fixture startedFixture = saveFixture("STARTED", Instant.parse("2020-08-29T01:00:00Z"));
        expectSessionStateConflict(startedFixture);
    }

    private void expectSessionStateConflict(Fixture fixture) throws Exception {
        mockMvc.perform(post(approvePath(fixture.revisionId()))
                .header(HttpHeaders.AUTHORIZATION, bearerToken(fixture.admin())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SESSION_STATE_CONFLICT"));
        entityManager.clear();
        assertThat(sessionRevisionRepository.findById(fixture.revisionId()))
            .get()
            .extracting(SessionRevision::getStatus)
            .isEqualTo(SessionRevisionStatus.PENDING);
        assertThat(auditEventRepository.findAll()).isEmpty();
    }

    private Fixture saveFixture(String suffix, Instant targetStartsAt) {
        Region region = saveRegion(suffix);
        AppUser admin = saveRegionAdmin("admin-" + suffix + "@example.com", region);
        AppUser operator = saveUser("operator-" + suffix + "@example.com", AppUserStatus.ACTIVE);
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.APPROVED,
            "Session revision " + suffix,
            "description",
            "location",
            "hours",
            "055-000-0000",
            "precautions",
            "age",
            "materials",
            "cancellation policy",
            ORIGINAL_STARTS_AT
        ));
        Instant targetEndsAt = targetStartsAt.plusSeconds(7_200);
        Instant targetCheckinOpenAt = targetStartsAt.minusSeconds(1_800);
        Instant targetCheckinCloseAt = targetStartsAt.plusSeconds(5_400);
        ContentSession targetSession = new ContentSession(
            content,
            region,
            targetStartsAt,
            targetEndsAt,
            targetCheckinOpenAt,
            targetCheckinCloseAt,
            20
        );
        targetSession.approve(admin, SUBMITTED_AT.minusSeconds(60));
        targetSession = contentSessionRepository.saveAndFlush(targetSession);
        SessionRevision revision = sessionRevisionRepository.saveAndFlush(new SessionRevision(
            content,
            region,
            targetSession,
            targetSession.getVersionNo(),
            CANDIDATE_STARTS_AT,
            CANDIDATE_ENDS_AT,
            CANDIDATE_CHECKIN_OPEN_AT,
            CANDIDATE_CHECKIN_CLOSE_AT,
            30,
            SessionRevisionStatus.PENDING,
            operator,
            SUBMITTED_AT,
            null,
            null,
            null
        ));
        return new Fixture(
            region,
            admin,
            operator,
            content.getContentId(),
            targetSession.getSessionId(),
            revision.getSessionRevisionId(),
            targetSession.getVersionNo()
        );
    }

    private AppUser saveRegionAdmin(String email, Region region) {
        AppUser admin = saveUser(email, AppUserStatus.ACTIVE);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
        return admin;
    }

    private AppUser saveUser(String email, AppUserStatus status) {
        return appUserRepository.saveAndFlush(new AppUser(
            email,
            "hashed-password",
            "user",
            "010-1234-5678",
            status
        ));
    }

    private Region saveRegion(String suffix) {
        return regionRepository.saveAndFlush(new Region("REGION-" + suffix, "region", true));
    }

    private String approvePath(Long revisionId) {
        return "/api/v1/region-admin/session-revisions/" + revisionId + "/approve";
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, user.getUserId());
    }

    private record Fixture(
        Region region,
        AppUser admin,
        AppUser operator,
        Long contentId,
        Long sessionId,
        Long revisionId,
        int baseSessionVersion
    ) {
    }
}
