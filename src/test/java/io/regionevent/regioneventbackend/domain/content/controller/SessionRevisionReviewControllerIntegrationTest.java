package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

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
@ExtendWith(OutputCaptureExtension.class)
class SessionRevisionReviewControllerIntegrationTest {

    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant STARTS_AT = Instant.parse("2026-08-29T01:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-08-29T03:00:00Z");
    private static final Instant CHECKIN_OPEN_AT = Instant.parse("2026-08-29T00:30:00Z");
    private static final Instant CHECKIN_CLOSE_AT = Instant.parse("2026-08-29T02:30:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private ContentSessionRepository contentSessionRepository;

    @Autowired
    private SessionRevisionRepository sessionRevisionRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 담당_지역의_심사_대기_회차_수정_요청을_고정_정렬하고_데이터를_변경하지_않는다(
        CapturedOutput output
    ) throws Exception {
        Region region = saveRegion("LIST");
        Region otherRegion = saveRegion("OTHER");
        AppUser regionAdmin = saveRegionAdmin("list-admin@example.com", region, AppUserStatus.ACTIVE);
        RevisionFixture firstTie = saveRevision(region, "first", SUBMITTED_AT, SessionRevisionStatus.PENDING);
        RevisionFixture secondTie = saveRevision(region, "second", SUBMITTED_AT, SessionRevisionStatus.PENDING);
        RevisionFixture later = saveRevision(
            region,
            "later",
            SUBMITTED_AT.plusSeconds(60),
            SessionRevisionStatus.PENDING
        );
        RevisionFixture otherRegionRevision = saveRevision(
            otherRegion,
            "other",
            SUBMITTED_AT.minusSeconds(60),
            SessionRevisionStatus.PENDING
        );
        RevisionFixture rejected = saveRevision(
            region,
            "rejected",
            SUBMITTED_AT.minusSeconds(60),
            SessionRevisionStatus.REJECTED
        );
        RevisionFixture deleted = saveRevision(
            region,
            "deleted",
            SUBMITTED_AT.minusSeconds(120),
            SessionRevisionStatus.PENDING
        );
        contentRepository.findById(deleted.contentId()).orElseThrow().softDelete(SUBMITTED_AT);
        contentRepository.flush();
        DatabaseSnapshot before = snapshot();

        mockMvc.perform(get("/api/v1/region-admin/session-revisions")
                .queryParam("status", "PENDING")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("심사 대기 회차 수정 요청 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.revisions.length()").value(3))
            .andExpect(jsonPath("$.data.revisions[0].revisionId").value(firstTie.revisionId().toString()))
            .andExpect(jsonPath("$.data.revisions[0].contentId").value(firstTie.contentId().toString()))
            .andExpect(jsonPath("$.data.revisions[0].contentTitle").value("회차 수정 요청 first"))
            .andExpect(jsonPath("$.data.revisions[0].targetSessionId").value(firstTie.targetSessionId().toString()))
            .andExpect(jsonPath("$.data.revisions[0].baseSessionVersion").value(firstTie.baseSessionVersion()))
            .andExpect(jsonPath("$.data.revisions[0].startsAt").value("2026-08-29T10:00:00+09:00"))
            .andExpect(jsonPath("$.data.revisions[0].endsAt").value("2026-08-29T12:00:00+09:00"))
            .andExpect(jsonPath("$.data.revisions[0].checkinOpenAt").value("2026-08-29T09:30:00+09:00"))
            .andExpect(jsonPath("$.data.revisions[0].checkinCloseAt").value("2026-08-29T11:30:00+09:00"))
            .andExpect(jsonPath("$.data.revisions[0].capacity").value(30))
            .andExpect(jsonPath("$.data.revisions[0].submittedAt").value("2026-08-01T00:00:00Z"))
            .andExpect(jsonPath("$.data.revisions[0].operator.operatorId").value(firstTie.operatorId().toString()))
            .andExpect(jsonPath("$.data.revisions[0].operator.name").value("운영자 first"))
            .andExpect(jsonPath("$.data.revisions[1].revisionId").value(secondTie.revisionId().toString()))
            .andExpect(jsonPath("$.data.revisions[2].revisionId").value(later.revisionId().toString()))
            .andExpect(jsonPath("$.data.revisions[*].revisionId")
                .value(not(hasItem(otherRegionRevision.revisionId().toString()))))
            .andExpect(jsonPath("$.data.revisions[*].revisionId")
                .value(not(hasItem(rejected.revisionId().toString()))))
            .andExpect(jsonPath("$.data.revisions[*].revisionId")
                .value(not(hasItem(deleted.revisionId().toString()))));

        assertDatabaseUnchanged(before, firstTie);
        assertThat(output).contains(
            "Pending session revisions queried.",
            "regionId=" + region.getRegionId(),
            "resultCount=3",
            "resultCode=SUCCESS"
        );
    }

    @Test
    void 대상_회차가_종결되어도_심사_대기_수정_요청을_반환한다() throws Exception {
        Region region = saveRegion("TERMINAL-TARGET");
        AppUser regionAdmin = saveRegionAdmin("terminal-target-admin@example.com", region, AppUserStatus.ACTIVE);
        RevisionFixture revision = saveRevision(region, "terminal-target", SUBMITTED_AT, SessionRevisionStatus.PENDING);
        ContentSession targetSession = contentSessionRepository.findById(revision.targetSessionId()).orElseThrow();
        targetSession.cancel(
            contentRepository.findById(revision.contentId()).orElseThrow().getOperator(),
            SUBMITTED_AT.plusSeconds(60),
            "운영상 취소"
        );
        contentSessionRepository.flush();

        mockMvc.perform(get("/api/v1/region-admin/session-revisions")
                .queryParam("status", "PENDING")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.revisions.length()").value(1))
            .andExpect(jsonPath("$.data.revisions[0].revisionId").value(revision.revisionId().toString()));
    }

    @Test
    void 심사_대기_회차_수정_요청이_없으면_빈_배열을_반환한다() throws Exception {
        Region region = saveRegion("EMPTY");
        AppUser regionAdmin = saveRegionAdmin("empty-admin@example.com", region, AppUserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/region-admin/session-revisions")
                .queryParam("status", "PENDING")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.revisions").isArray())
            .andExpect(jsonPath("$.data.revisions").isEmpty());
    }

    @Test
    void 목록_조회는_인증과_활성_지역_관리자_권한을_요구한다() throws Exception {
        Region region = saveRegion("AUTHORIZATION");
        AppUser visitor = saveUser("visitor@example.com", "방문자", AppUserStatus.ACTIVE);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(visitor, UserRole.VISITOR, null));
        AppUser inactiveAdmin = saveRegionAdmin("inactive-admin@example.com", region, AppUserStatus.WITHDRAWING);

        mockMvc.perform(get("/api/v1/region-admin/session-revisions")
                .queryParam("status", "PENDING"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        for (AppUser unauthorizedUser : List.of(visitor, inactiveAdmin)) {
            mockMvc.perform(get("/api/v1/region-admin/session-revisions")
                    .queryParam("status", "PENDING")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(unauthorizedUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Test
    void status가_없거나_PENDING이_아니면_INVALID_INPUT으로_거부한다(CapturedOutput output) throws Exception {
        Region region = saveRegion("INVALID-STATUS");
        AppUser regionAdmin = saveRegionAdmin("invalid-status-admin@example.com", region, AppUserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/region-admin/session-revisions")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        for (String invalidStatus : List.of("APPROVED", "pending", "PENDING ")) {
            mockMvc.perform(get("/api/v1/region-admin/session-revisions")
                    .queryParam("status", invalidStatus)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken(regionAdmin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        assertThat(output).contains(
            "Pending session revisions queried.",
            "regionId=" + region.getRegionId(),
            "resultCode=INVALID_INPUT"
        );
    }

    private RevisionFixture saveRevision(
        Region region,
        String suffix,
        Instant submittedAt,
        SessionRevisionStatus status
    ) {
        AppUser operator = saveUser(suffix + "-operator@example.com", "운영자 " + suffix, AppUserStatus.ACTIVE);
        AppUser reviewer = saveUser(suffix + "-reviewer@example.com", "심사자 " + suffix, AppUserStatus.ACTIVE);
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.APPROVED,
            "회차 수정 요청 " + suffix,
            "콘텐츠 설명",
            "콘텐츠 위치",
            "운영 시간",
            "055-111-1111",
            "유의사항",
            "연령 조건",
            "준비물",
            "취소 정책",
            STARTS_AT
        ));
        ContentSession targetSession = new ContentSession(
            content,
            region,
            STARTS_AT.minusSeconds(604_800),
            ENDS_AT.minusSeconds(604_800),
            CHECKIN_OPEN_AT.minusSeconds(604_800),
            CHECKIN_CLOSE_AT.minusSeconds(604_800),
            20
        );
        targetSession.approve(reviewer, submittedAt.minusSeconds(60));
        targetSession = contentSessionRepository.saveAndFlush(targetSession);
        SessionRevision revision = sessionRevisionRepository.saveAndFlush(new SessionRevision(
            content,
            region,
            targetSession,
            targetSession.getVersionNo(),
            STARTS_AT,
            ENDS_AT,
            CHECKIN_OPEN_AT,
            CHECKIN_CLOSE_AT,
            30,
            status,
            operator,
            submittedAt,
            status == SessionRevisionStatus.PENDING ? null : submittedAt.plusSeconds(60),
            status == SessionRevisionStatus.PENDING ? null : reviewer,
            status == SessionRevisionStatus.REJECTED ? "반려 사유" : null
        ));
        return new RevisionFixture(
            content.getContentId(),
            targetSession.getSessionId(),
            revision.getSessionRevisionId(),
            operator.getUserId(),
            targetSession.getVersionNo()
        );
    }

    private AppUser saveRegionAdmin(String email, Region region, AppUserStatus status) {
        AppUser user = saveUser(email, "지역 관리자", status);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, UserRole.REGION_ADMIN, region));
        return user;
    }

    private AppUser saveUser(String email, String name, AppUserStatus status) {
        return appUserRepository.saveAndFlush(new AppUser(
            email,
            "hashed-password",
            name,
            "010-1234-5678",
            status
        ));
    }

    private Region saveRegion(String suffix) {
        return regionRepository.saveAndFlush(new Region("REGION-" + suffix, "테스트 지역", true));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private DatabaseSnapshot snapshot() {
        return new DatabaseSnapshot(
            contentRepository.count(),
            contentSessionRepository.count(),
            sessionRevisionRepository.count(),
            auditEventRepository.count()
        );
    }

    private void assertDatabaseUnchanged(DatabaseSnapshot before, RevisionFixture fixture) {
        entityManager.clear();
        assertThat(snapshot()).isEqualTo(before);
        assertThat(sessionRevisionRepository.findById(fixture.revisionId()))
            .get()
            .satisfies(revision -> {
                assertThat(revision.getStatus()).isEqualTo(SessionRevisionStatus.PENDING);
                assertThat(revision.getReviewedAt()).isNull();
                assertThat(revision.getTargetSession().getSessionId()).isEqualTo(fixture.targetSessionId());
            });
        assertThat(contentSessionRepository.findById(fixture.targetSessionId()))
            .get()
            .satisfies(session -> assertThat(session.getStatus()).isEqualTo(ContentSessionStatus.SCHEDULED));
    }

    private record RevisionFixture(
        Long contentId,
        Long targetSessionId,
        Long revisionId,
        Long operatorId,
        int baseSessionVersion
    ) {
    }

    private record DatabaseSnapshot(
        long contents,
        long sessions,
        long revisions,
        long auditEvents
    ) {
    }
}
