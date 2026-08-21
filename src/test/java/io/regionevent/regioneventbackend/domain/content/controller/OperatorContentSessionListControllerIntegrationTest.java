package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.content.repository.SessionRevisionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
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
class OperatorContentSessionListControllerIntegrationTest {

    private static final Instant BASE_STARTS_AT = Instant.parse("2026-08-22T01:00:00Z");
    private static final Instant REVIEWED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-02T00:00:00Z");

    private final MockMvc mockMvc;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final SessionRevisionRepository sessionRevisionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final AuditEventRepository auditEventRepository;
    private final EntityManager entityManager;

    @Autowired
    OperatorContentSessionListControllerIntegrationTest(
        MockMvc mockMvc,
        JwtAccessTokenService jwtAccessTokenService,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        SessionRevisionRepository sessionRevisionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
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
        this.sessionRevisionRepository = sessionRevisionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.auditEventRepository = auditEventRepository;
        this.entityManager = entityManager;
    }

    @Test
    void getOperatorContentSessions_전체_상태와_현재_변경_요청을_고정_정렬로_조회하고_상태를_변경하지_않는다()
        throws Exception {

        Region region = saveRegion("LIST-NORMAL");
        AppUser operator = saveOperator("list-normal@example.com", region);
        Content content = saveContent(region, operator, ContentStatus.REJECTED, "반려 콘텐츠");
        ContentSession scheduled = saveSession(
            content,
            region,
            operator,
            ContentSessionStatus.SCHEDULED,
            BASE_STARTS_AT.minusSeconds(86_400)
        );
        ContentSession rejected = saveSession(
            content,
            region,
            operator,
            ContentSessionStatus.REJECTED,
            BASE_STARTS_AT.minusSeconds(86_400)
        );
        ContentSession pending = saveSession(
            content,
            region,
            operator,
            ContentSessionStatus.PENDING,
            BASE_STARTS_AT
        );
        ContentSession completed = saveSession(
            content,
            region,
            operator,
            ContentSessionStatus.COMPLETED,
            BASE_STARTS_AT.plusSeconds(86_400)
        );
        ContentSession cancelled = saveSession(
            content,
            region,
            operator,
            ContentSessionStatus.CANCELLED,
            BASE_STARTS_AT.plusSeconds(172_800)
        );
        SessionRevision revision = savePendingRevision(content, region, scheduled, operator);
        Long contentId = content.getContentId();
        PersistentSnapshot before = snapshot(contentId);

        mockMvc.perform(get("/api/v1/operator/contents/{contentId}/sessions", contentId)
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 콘텐츠 회차 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(contentId.toString()))
            .andExpect(jsonPath("$.data.sessions.length()").value(5))
            .andExpect(jsonPath("$.data.sessions[0].sessionId").value(scheduled.getSessionId().toString()))
            .andExpect(jsonPath("$.data.sessions[1].sessionId").value(rejected.getSessionId().toString()))
            .andExpect(jsonPath("$.data.sessions[2].sessionId").value(pending.getSessionId().toString()))
            .andExpect(jsonPath("$.data.sessions[3].sessionId").value(completed.getSessionId().toString()))
            .andExpect(jsonPath("$.data.sessions[4].sessionId").value(cancelled.getSessionId().toString()))
            .andExpect(jsonPath("$.data.sessions[0].status").value("SCHEDULED"))
            .andExpect(jsonPath("$.data.sessions[0].version").value(scheduled.getVersionNo()))
            .andExpect(jsonPath("$.data.sessions[0].startsAt").value("2026-08-21T10:00:00+09:00"))
            .andExpect(jsonPath("$.data.sessions[0].endsAt").value("2026-08-21T12:00:00+09:00"))
            .andExpect(jsonPath("$.data.sessions[0].checkinOpenAt").value("2026-08-21T09:30:00+09:00"))
            .andExpect(jsonPath("$.data.sessions[0].checkinCloseAt").value("2026-08-21T11:30:00+09:00"))
            .andExpect(jsonPath("$.data.sessions[0].capacity").value(30))
            .andExpect(jsonPath("$.data.sessions[0].remainingCapacity").value(30))
            .andExpect(jsonPath("$.data.sessions[0].pendingChangeRequest.revisionId")
                .value(revision.getSessionRevisionId().toString()))
            .andExpect(jsonPath("$.data.sessions[0].pendingChangeRequest.status").value("PENDING"))
            .andExpect(jsonPath("$.data.sessions[0].pendingChangeRequest.baseSessionVersion")
                .value(scheduled.getVersionNo()))
            .andExpect(jsonPath("$.data.sessions[0].pendingChangeRequest.candidate.startsAt")
                .value("2026-08-29T10:00:00+09:00"))
            .andExpect(jsonPath("$.data.sessions[0].pendingChangeRequest.candidate.capacity").value(40))
            .andExpect(jsonPath("$.data.sessions[0].pendingChangeRequest.submittedAt")
                .value("2026-08-02T00:00:00Z"))
            .andExpect(jsonPath("$.data.sessions[1].rejectReason").value("일정 기준을 충족하지 않습니다."))
            .andExpect(jsonPath("$.data.sessions[2].pendingChangeRequest").value(nullValue()))
            .andExpect(jsonPath("$.data.sessions[3].completedAt").value("2026-08-01T00:00:00Z"))
            .andExpect(jsonPath("$.data.sessions[4].cancelledAt").value("2026-08-01T00:00:00Z"))
            .andExpect(jsonPath("$.data.sessions[4].cancellationReason").value("운영 사정으로 취소합니다."))
            .andExpect(jsonPath("$.data.sessions[0].price").doesNotExist())
            .andExpect(jsonPath("$.data.page").doesNotExist())
            .andExpect(jsonPath("$.data.status").doesNotExist());

        assertThat(snapshot(contentId)).isEqualTo(before);
    }

    @Test
    void getOperatorContentSessions_변경_요청_제출_후_취소된_회차에도_현재_변경_요청을_반환한다()
        throws Exception {

        Region region = saveRegion("LIST-CANCELLED-REVISION");
        AppUser operator = saveOperator("list-cancelled-revision@example.com", region);
        Content content = saveContent(region, operator, ContentStatus.APPROVED, "취소 회차 콘텐츠");
        ContentSession contentSession = saveSession(
            content,
            region,
            operator,
            ContentSessionStatus.SCHEDULED,
            BASE_STARTS_AT
        );
        SessionRevision revision = savePendingRevision(content, region, contentSession, operator);
        contentSession.cancel(
            operator,
            REVIEWED_AT.plusSeconds(3_600),
            "변경 요청 제출 후 회차를 취소합니다."
        );
        contentSessionRepository.saveAndFlush(contentSession);

        mockMvc.perform(get("/api/v1/operator/contents/{contentId}/sessions", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessions.length()").value(1))
            .andExpect(jsonPath("$.data.sessions[0].status").value("CANCELLED"))
            .andExpect(jsonPath("$.data.sessions[0].pendingChangeRequest.revisionId")
                .value(revision.getSessionRevisionId().toString()))
            .andExpect(jsonPath("$.data.sessions[0].pendingChangeRequest.status").value("PENDING"));
    }

    @Test
    void getOperatorContentSessions_회차가_없으면_빈_배열을_반환한다() throws Exception {
        Region region = saveRegion("LIST-EMPTY");
        AppUser operator = saveOperator("list-empty@example.com", region);
        Content content = saveContent(region, operator, ContentStatus.PENDING, "빈 콘텐츠");

        mockMvc.perform(get("/api/v1/operator/contents/{contentId}/sessions", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessions").isEmpty());
    }

    @Test
    void getOperatorContentSessions_인증과_역할_지역_소유권을_검증한다() throws Exception {
        Region region = saveRegion("LIST-AUTH");
        Region otherRegion = saveRegion("LIST-AUTH-OTHER");
        AppUser owner = saveOperator("list-owner@example.com", region);
        AppUser otherOwner = saveOperator("list-other-owner@example.com", region);
        AppUser otherRegionalOperator = saveOperator("list-other-region@example.com", otherRegion);
        AppUser visitor = saveUser("list-visitor@example.com");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(visitor, UserRole.VISITOR, null));
        Content content = saveContent(region, owner, ContentStatus.APPROVED, "소유 콘텐츠");

        mockMvc.perform(get("/api/v1/operator/contents/{contentId}/sessions", content.getContentId()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        expectForbidden(content, visitor);
        expectForbidden(content, otherOwner);
        expectForbidden(content, otherRegionalOperator);
    }

    @Test
    void getOperatorContentSessions_없는_콘텐츠와_소프트_삭제_콘텐츠는_찾을수없음을_반환한다()
        throws Exception {

        Region region = saveRegion("LIST-NOT-FOUND");
        AppUser operator = saveOperator("list-not-found@example.com", region);
        Content deletedContent = saveContent(region, operator, ContentStatus.PENDING, "삭제 콘텐츠");
        deletedContent.softDelete(Instant.parse("2026-08-03T00:00:00Z"));
        contentRepository.saveAndFlush(deletedContent);

        mockMvc.perform(get("/api/v1/operator/contents/{contentId}/sessions", deletedContent.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/operator/contents/999999999/sessions")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getOperatorContentSessions_경로_식별자_오류를_구분한다() throws Exception {
        Region region = saveRegion("LIST-INVALID-ID");
        AppUser operator = saveOperator("list-invalid-id@example.com", region);

        mockMvc.perform(get("/api/v1/operator/contents/0/sessions")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/operator/contents/not-a-number/sessions")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(operator)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    private void expectForbidden(Content content, AppUser requester) throws Exception {
        mockMvc.perform(get("/api/v1/operator/contents/{contentId}/sessions", content.getContentId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(requester)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private PersistentSnapshot snapshot(Long contentId) {
        entityManager.flush();
        entityManager.clear();
        Content content = contentRepository.findById(contentId).orElseThrow();
        List<SessionSnapshot> sessions = contentSessionRepository
            .findByContentContentIdOrderByStartsAtAscSessionIdAsc(contentId)
            .stream()
            .map(SessionSnapshot::from)
            .toList();
        List<RevisionSnapshot> revisions = sessionRevisionRepository.findAll().stream()
            .filter(revision -> revision.getTargetSession().getContent().getContentId().equals(contentId))
            .map(RevisionSnapshot::from)
            .toList();
        return new PersistentSnapshot(
            new ContentSnapshot(
                content.getStatus(),
                content.getVersionNo(),
                content.getDeletedAt(),
                content.getUpdatedAt()
            ),
            sessions,
            revisions,
            contentRepository.count(),
            contentSessionRepository.count(),
            sessionRevisionRepository.count(),
            capacityHoldRepository.count(),
            reservationRepository.count(),
            auditEventRepository.count()
        );
    }

    private SessionRevision savePendingRevision(
        Content content,
        Region region,
        ContentSession targetSession,
        AppUser requestedBy
    ) {
        Instant startsAt = BASE_STARTS_AT.plusSeconds(604_800);
        return sessionRevisionRepository.saveAndFlush(new SessionRevision(
            content,
            region,
            targetSession,
            targetSession.getVersionNo(),
            startsAt,
            startsAt.plusSeconds(7_200),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(5_400),
            40,
            SessionRevisionStatus.PENDING,
            requestedBy,
            SUBMITTED_AT,
            null,
            null,
            null
        ));
    }

    private ContentSession saveSession(
        Content content,
        Region region,
        AppUser reviewer,
        ContentSessionStatus status,
        Instant startsAt
    ) {
        ContentSession session = new ContentSession(
            content,
            region,
            startsAt,
            startsAt.plusSeconds(7_200),
            startsAt.minusSeconds(1_800),
            startsAt.plusSeconds(5_400),
            30
        );
        if (status == ContentSessionStatus.SCHEDULED) {
            session.approve(reviewer, REVIEWED_AT);
        } else if (status == ContentSessionStatus.REJECTED) {
            session.reject(reviewer, REVIEWED_AT, "일정 기준을 충족하지 않습니다.");
        } else if (status == ContentSessionStatus.COMPLETED) {
            session.approve(reviewer, REVIEWED_AT);
            session.complete(REVIEWED_AT);
        } else if (status == ContentSessionStatus.CANCELLED) {
            session.approve(reviewer, REVIEWED_AT);
            session.cancel(reviewer, REVIEWED_AT, "운영 사정으로 취소합니다.");
        }
        return contentSessionRepository.saveAndFlush(session);
    }

    private Content saveContent(
        Region region,
        AppUser operator,
        ContentStatus status,
        String title
    ) {
        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            status,
            title,
            "설명",
            "위치",
            "운영 시간",
            "055-000-0000",
            "유의사항",
            "연령 조건",
            "준비물",
            "취소 정책",
            Instant.parse("2026-08-20T00:00:00Z")
        ));
    }

    private AppUser saveOperator(String loginIdentifier, Region region) {
        AppUser operator = saveUser(loginIdentifier);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        return operator;
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private Region saveRegion(String suffix) {
        return regionRepository.saveAndFlush(new Region("REGION-" + suffix, "테스트 지역", true));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(
            jwtAccessTokenService,
            user.getUserId()
        );
    }

    private record PersistentSnapshot(
        ContentSnapshot content,
        List<SessionSnapshot> sessions,
        List<RevisionSnapshot> revisions,
        long contentCount,
        long sessionCount,
        long revisionCount,
        long holdCount,
        long reservationCount,
        long auditCount
    ) {

        private PersistentSnapshot {
            sessions = List.copyOf(sessions);
            revisions = List.copyOf(revisions);
        }
    }

    private record ContentSnapshot(
        ContentStatus status,
        int version,
        Instant deletedAt,
        Instant updatedAt
    ) {
    }

    private record SessionSnapshot(
        Long sessionId,
        ContentSessionStatus status,
        int version,
        int capacity,
        int remainingCapacity,
        String rejectReason,
        Instant cancelledAt,
        String cancellationReason,
        Instant completedAt,
        Instant updatedAt
    ) {

        private static SessionSnapshot from(ContentSession session) {
            return new SessionSnapshot(
                session.getSessionId(),
                session.getStatus(),
                session.getVersionNo(),
                session.getCapacity(),
                session.getRemainingCapacity(),
                session.getRejectReason(),
                session.getCancelledAt(),
                session.getCancellationReason(),
                session.getCompletedAt(),
                session.getUpdatedAt()
            );
        }
    }

    private record RevisionSnapshot(
        Long revisionId,
        SessionRevisionStatus status,
        int baseSessionVersion,
        Instant startsAt,
        Instant endsAt,
        Instant checkinOpenAt,
        Instant checkinCloseAt,
        int capacity,
        Instant submittedAt,
        Instant reviewedAt,
        String rejectReason
    ) {

        private static RevisionSnapshot from(SessionRevision revision) {
            return new RevisionSnapshot(
                revision.getSessionRevisionId(),
                revision.getStatus(),
                revision.getBaseSessionVersion(),
                revision.getStartsAt(),
                revision.getEndsAt(),
                revision.getCheckinOpenAt(),
                revision.getCheckinCloseAt(),
                revision.getCapacity(),
                revision.getSubmittedAt(),
                revision.getReviewedAt(),
                revision.getRejectReason()
            );
        }
    }
}
