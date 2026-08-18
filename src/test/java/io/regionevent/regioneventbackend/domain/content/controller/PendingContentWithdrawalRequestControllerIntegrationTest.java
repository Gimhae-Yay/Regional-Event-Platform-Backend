package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentWithdrawalRequestRepository;
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
@Sql(statements = """
    CREATE ALIAS IF NOT EXISTS UNIX_TIMESTAMP FOR "io.regionevent.regioneventbackend.support.jpa.H2MySqlCompatibilityFunctions.unixTimestamp"
    """)
class PendingContentWithdrawalRequestControllerIntegrationTest {

    private static final String PATH = "/api/v1/region-admin/content-withdrawal-requests";
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-16T04:00:00Z");

    private final MockMvc mockMvc;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentWithdrawalRequestRepository withdrawalRequestRepository;
    private final AuditEventRepository auditEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    @Autowired
    PendingContentWithdrawalRequestControllerIntegrationTest(
        MockMvc mockMvc,
        JwtAccessTokenService jwtAccessTokenService,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentWithdrawalRequestRepository withdrawalRequestRepository,
        AuditEventRepository auditEventRepository,
        JdbcTemplate jdbcTemplate,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.auditEventRepository = auditEventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    @Test
    void 담당지역_대기_요청만_고정순서로_조회하고_상태와_감사를_변경하지_않는다()
        throws Exception {
        Region assignedRegion = saveRegion("WITHDRAWAL-API-A");
        Region otherRegion = saveRegion("WITHDRAWAL-API-B");
        AppUser admin = saveRegionAdmin("withdrawal-admin@example.com", assignedRegion);
        AppUser requester = saveUser("withdrawal-requester@example.com", "김요청");
        AppUser unlinkedRequester = saveUser("withdrawal-unlinked@example.com", "탈퇴 요청자");
        ContentWithdrawalRequest later = savePendingRequest(
            saveContent(assignedRegion, requester, ContentStatus.PUBLISHED, "나중 요청"),
            requester,
            "1",
            REQUESTED_AT.plusSeconds(1)
        );
        ContentWithdrawalRequest tiedFirst = savePendingRequest(
            saveContent(assignedRegion, requester, ContentStatus.PUBLISHED, "첫 요청"),
            requester,
            "2",
            REQUESTED_AT
        );
        ContentWithdrawalRequest tiedSecond = savePendingRequest(
            saveContent(assignedRegion, unlinkedRequester, ContentStatus.PUBLISHED, "탈퇴자 요청"),
            unlinkedRequester,
            "3",
            REQUESTED_AT
        );
        savePendingRequest(
            saveContent(otherRegion, requester, ContentStatus.PUBLISHED, "다른 지역"),
            requester,
            "4",
            REQUESTED_AT.minusSeconds(1)
        );
        Content deletedContent = saveContent(
            assignedRegion,
            requester,
            ContentStatus.APPROVED,
            "삭제 콘텐츠"
        );
        ContentWithdrawalRequest deletedRequest = savePendingRequest(
            deletedContent,
            requester,
            "5",
            REQUESTED_AT.minusSeconds(1)
        );
        deletedContent.softDelete(REQUESTED_AT.plusSeconds(10));
        contentRepository.saveAndFlush(deletedContent);
        ContentWithdrawalRequest approvedRequest = savePendingRequest(
            saveContent(assignedRegion, requester, ContentStatus.PUBLISHED, "처리된 요청"),
            requester,
            "6",
            REQUESTED_AT.minusSeconds(1)
        );
        approvedRequest.approve(admin, REQUESTED_AT.plusSeconds(10));
        withdrawalRequestRepository.saveAndFlush(approvedRequest);
        savePendingRequest(
            saveContent(assignedRegion, requester, ContentStatus.APPROVED, "비공개 콘텐츠"),
            requester,
            "7",
            REQUESTED_AT.minusSeconds(1)
        );
        withdrawalRequestRepository.unlinkRequesterByUserId(unlinkedRequester.getUserId());
        entityManager.flush();
        entityManager.clear();
        DatabaseSnapshot before = snapshot();

        mockMvc.perform(get(PATH).queryParam("status", "PENDING")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message")
                .value("전체 콘텐츠 철회 요청 대기 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.withdrawalRequests.length()").value(3))
            .andExpect(jsonPath("$.data.withdrawalRequests[0].withdrawalRequestId")
                .value(tiedFirst.getContentWithdrawalRequestId().toString()))
            .andExpect(jsonPath("$.data.withdrawalRequests[0].requester.userId")
                .value(requester.getUserId().toString()))
            .andExpect(jsonPath("$.data.withdrawalRequests[0].requester.name").value("김요청"))
            .andExpect(jsonPath("$.data.withdrawalRequests[1].withdrawalRequestId")
                .value(tiedSecond.getContentWithdrawalRequestId().toString()))
            .andExpect(jsonPath("$.data.withdrawalRequests[1].requester").value(nullValue()))
            .andExpect(jsonPath("$.data.withdrawalRequests[1].requestedAt")
                .value("2026-08-16T04:00:00Z"))
            .andExpect(jsonPath("$.data.withdrawalRequests[2].withdrawalRequestId")
                .value(later.getContentWithdrawalRequestId().toString()))
            .andExpect(jsonPath("$.data.withdrawalRequests[2].contentStatus")
                .value("PUBLISHED"))
            .andExpect(jsonPath("$.data.withdrawalRequests[0].requestReason").doesNotExist())
            .andExpect(jsonPath("$.data.withdrawalRequests[0].idempotencyKeyHash").doesNotExist())
            .andExpect(jsonPath("$.data.withdrawalRequests[0].reviewedAt").doesNotExist())
            .andExpect(jsonPath("$.data.withdrawalRequests[0].invalidatedAt").doesNotExist())
            .andExpect(jsonPath("$..email").doesNotExist())
            .andExpect(jsonPath("$..phone").doesNotExist());

        assertThat(snapshot()).isEqualTo(before);
        assertThat(withdrawalRequestRepository.findById(deletedRequest.getContentWithdrawalRequestId()))
            .hasValueSatisfying(request -> assertThat(request.getStatus().name()).isEqualTo("PENDING"));
    }

    @Test
    void 담당지역에_대상이_없으면_다른_지역_개수를_숨기고_빈_목록을_반환한다()
        throws Exception {
        Region assignedRegion = saveRegion("WITHDRAWAL-EMPTY-A");
        Region otherRegion = saveRegion("WITHDRAWAL-EMPTY-B");
        AppUser admin = saveRegionAdmin("empty-withdrawal-admin@example.com", assignedRegion);
        AppUser requester = saveUser("empty-withdrawal-requester@example.com", "요청자");
        savePendingRequest(
            saveContent(otherRegion, requester, ContentStatus.PUBLISHED, "다른 지역 요청"),
            requester,
            "8",
            REQUESTED_AT
        );

        mockMvc.perform(get(PATH).queryParam("status", "PENDING")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.withdrawalRequests").isEmpty());
    }

    @Test
    void 인증과_권한을_status보다_먼저_검증한다() throws Exception {
        Region region = saveRegion("WITHDRAWAL-AUTH");
        AppUser admin = saveRegionAdmin("auth-withdrawal-admin@example.com", region);
        AppUser visitor = saveUser("auth-withdrawal-visitor@example.com", "방문자");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            visitor,
            UserRole.VISITOR,
            null
        ));

        mockMvc.perform(get(PATH).queryParam("status", "PENDING"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get(PATH).queryParam("status", "APPROVED")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(visitor)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get(PATH).queryParam("status", "")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get(PATH).queryParam("status", "APPROVED")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private Region saveRegion(String code) {
        return regionRepository.saveAndFlush(new Region(code, "테스트 지역", true));
    }

    private AppUser saveRegionAdmin(String loginIdentifier, Region region) {
        AppUser user = saveUser(loginIdentifier, "지역 관리자");
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            user,
            UserRole.REGION_ADMIN,
            region
        ));
        return user;
    }

    private AppUser saveUser(String loginIdentifier, String name) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            name,
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
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
            "연령",
            "준비물",
            "취소 정책",
            Instant.parse("2026-08-01T00:00:00Z")
        ));
    }

    private ContentWithdrawalRequest savePendingRequest(
        Content content,
        AppUser requester,
        String hashCharacter,
        Instant requestedAt
    ) {
        return withdrawalRequestRepository.saveAndFlush(ContentWithdrawalRequest.createPending(
            content,
            requester,
            hashCharacter.repeat(64),
            "비공개 철회 요청 사유",
            requestedAt
        ));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }

    private DatabaseSnapshot snapshot() {
        List<Map<String, Object>> requests = jdbcTemplate.queryForList("""
            SELECT content_withdrawal_request_id, content_id, requested_by_user_id, status,
                request_reason, requested_at, reviewed_at, reviewed_by_user_id,
                rejection_reason, invalidated_at, invalidated_by_user_id, invalidation_reason
            FROM content_withdrawal_request
            ORDER BY content_withdrawal_request_id
            """);
        List<Map<String, Object>> contents = jdbcTemplate.queryForList("""
            SELECT content_id, status, deleted_at, version_no, updated_at
            FROM content
            ORDER BY content_id
            """);
        return new DatabaseSnapshot(
            List.copyOf(requests),
            List.copyOf(contents),
            auditEventRepository.count()
        );
    }

    private record DatabaseSnapshot(
        List<Map<String, Object>> withdrawalRequests,
        List<Map<String, Object>> contents,
        long auditEvents
    ) {
    }
}
