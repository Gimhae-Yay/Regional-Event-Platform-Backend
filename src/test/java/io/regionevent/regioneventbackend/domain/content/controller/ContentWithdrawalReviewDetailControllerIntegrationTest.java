package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
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
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class ContentWithdrawalReviewDetailControllerIntegrationTest {

    private static final Instant PUBLISH_AT = Instant.parse("2026-08-01T00:00:00Z");
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
    ContentWithdrawalReviewDetailControllerIntegrationTest(
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
    void 담당_지역의_대기_요청_상세를_반환하고_데이터를_변경하지_않는다(
        CapturedOutput output
    ) throws Exception {
        Fixture fixture = createFixture("SUCCESS");
        DatabaseSnapshot before = snapshot(fixture);

        mockMvc.perform(get(
                "/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}",
                fixture.requestId()
            ).header(HttpHeaders.AUTHORIZATION, bearerToken(fixture.admin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message")
                .value("전체 콘텐츠 철회 요청 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.withdrawalRequestId")
                .value(fixture.requestId().toString()))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.content.contentId").value(fixture.contentId().toString()))
            .andExpect(jsonPath("$.data.content.contentType").value("EVENT_EXPERIENCE"))
            .andExpect(jsonPath("$.data.content.title").value("김해 가야문화 체험"))
            .andExpect(jsonPath("$.data.content.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.content.publishAt")
                .value("2026-08-01T09:00:00+09:00"))
            .andExpect(jsonPath("$.data.requester.userId")
                .value(fixture.requesterId().toString()))
            .andExpect(jsonPath("$.data.requester.name").value("김운영"))
            .andExpect(jsonPath("$.data.requestReason").value("운영 계획 변경"))
            .andExpect(jsonPath("$.data.requestedAt").value("2026-08-16T04:00:00Z"));

        assertUnchanged(before, fixture);
        assertThat(output).contains(
            "Content withdrawal review detail queried.",
            "regionId=" + fixture.regionId(),
            "withdrawalRequestId=" + fixture.requestId(),
            "resultCode=SUCCESS"
        ).doesNotContain(
            "김운영",
            "운영 계획 변경",
            "a".repeat(64)
        );
    }

    @Test
    void 인증과_활성_지역_관리자_권한을_요구하고_실패해도_원문_ID나_데이터를_변경하지_않는다(
        CapturedOutput output
    ) throws Exception {
        Fixture fixture = createFixture("AUTHORIZATION");
        AppUser visitor = saveUser("visitor-auth", "방문자", AppUserStatus.ACTIVE);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            visitor,
            UserRole.VISITOR,
            null
        ));
        AppUser inactiveAdmin = saveRegionAdmin(
            "inactive-admin",
            regionRepository.findById(fixture.regionId()).orElseThrow(),
            AppUserStatus.WITHDRAWING
        );
        DatabaseSnapshot before = snapshot(fixture);

        mockMvc.perform(get(
                "/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}",
                fixture.requestId()
            ))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get(
                "/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}",
                fixture.requestId()
            ).header(HttpHeaders.AUTHORIZATION, bearerToken(visitor)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get(
                "/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}",
                fixture.requestId()
            ).header(HttpHeaders.AUTHORIZATION, bearerToken(inactiveAdmin)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertUnchanged(before, fixture);
        assertThat(output).contains(
            "withdrawalRequestId=null, resultCode=UNAUTHENTICATED",
            "uri=/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}, status=401"
        ).doesNotContain(
            "uri=/api/v1/region-admin/content-withdrawal-requests/" + fixture.requestId()
        );
    }

    @Test
    void 다른_지역_요청과_종결_요청을_구분해_노출하지_않고_데이터를_변경하지_않는다(
        CapturedOutput output
    ) throws Exception {
        Fixture fixture = createFixture("ISOLATION");
        Region otherRegion = saveRegion("OTHER");
        AppUser otherAdmin = saveRegionAdmin("other-admin", otherRegion, AppUserStatus.ACTIVE);
        DatabaseSnapshot pendingBefore = snapshot(fixture);

        mockMvc.perform(get(
                "/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}",
                fixture.requestId()
            ).header(HttpHeaders.AUTHORIZATION, bearerToken(otherAdmin)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.data").value((Object) null));

        assertUnchanged(pendingBefore, fixture);
        ContentWithdrawalRequest request = withdrawalRequestRepository.findById(
            fixture.requestId()
        ).orElseThrow();
        request.reject(fixture.admin(), REQUESTED_AT.plusSeconds(60), "반려 사유");
        withdrawalRequestRepository.saveAndFlush(request);
        DatabaseSnapshot terminalBefore = snapshot(fixture);

        mockMvc.perform(get(
                "/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}",
                fixture.requestId()
            ).header(HttpHeaders.AUTHORIZATION, bearerToken(fixture.admin())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.data").value((Object) null));

        assertUnchanged(terminalBefore, fixture);
        assertThat(output).doesNotContain("김운영", "운영 계획 변경", "반려 사유");
    }

    @Test
    void 요청자_연결이_제거되면_콘텐츠_운영자로_재식별하지_않고_null을_반환한다()
        throws Exception {
        Fixture fixture = createFixture("UNLINKED");
        withdrawalRequestRepository.unlinkRequesterByUserId(fixture.requesterId());
        entityManager.clear();
        DatabaseSnapshot before = snapshot(fixture);

        mockMvc.perform(get(
                "/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}",
                fixture.requestId()
            ).header(HttpHeaders.AUTHORIZATION, bearerToken(fixture.admin())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.requester").value((Object) null));

        assertUnchanged(before, fixture);
    }

    @Test
    void PENDING_요청의_콘텐츠가_PUBLISHED가_아니면_정합성_오류로_처리한다()
        throws Exception {
        Fixture fixture = createFixture("INVALID-STATUS");
        jdbcTemplate.update(
            "UPDATE content SET status = 'APPROVED' WHERE content_id = ?",
            fixture.contentId()
        );
        entityManager.clear();
        DatabaseSnapshot before = snapshot(fixture);

        expectInternalServerError(fixture);

        assertUnchanged(before, fixture);
    }

    @Test
    void PENDING_요청의_콘텐츠가_삭제됐으면_정합성_오류로_처리한다() throws Exception {
        Fixture fixture = createFixture("DELETED", ContentStatus.APPROVED);
        Content content = contentRepository.findById(fixture.contentId()).orElseThrow();
        content.softDelete(REQUESTED_AT.plusSeconds(60));
        contentRepository.flush();
        DatabaseSnapshot before = snapshot(fixture);

        expectInternalServerError(fixture);

        assertUnchanged(before, fixture);
    }

    private void expectInternalServerError(Fixture fixture) throws Exception {
        mockMvc.perform(get(
                "/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}",
                fixture.requestId()
            ).header(HttpHeaders.AUTHORIZATION, bearerToken(fixture.admin())))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.statusCode").value(500))
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
            .andExpect(jsonPath("$.data").value((Object) null));
    }

    private Fixture createFixture(String suffix) {
        return createFixture(suffix, ContentStatus.PUBLISHED);
    }

    private Fixture createFixture(String suffix, ContentStatus contentStatus) {
        Region region = saveRegion(suffix);
        AppUser admin = saveRegionAdmin("admin-" + suffix, region, AppUserStatus.ACTIVE);
        AppUser requester = saveUser("operator-" + suffix, "김운영", AppUserStatus.ACTIVE);
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            requester,
            ContentType.EVENT_EXPERIENCE,
            contentStatus,
            "김해 가야문화 체험",
            "김해 가야 문화를 체험합니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "하루 전 취소 가능",
            PUBLISH_AT
        ));
        ContentWithdrawalRequest request = withdrawalRequestRepository.saveAndFlush(
            ContentWithdrawalRequest.createPending(
                content,
                requester,
                "a".repeat(64),
                "운영 계획 변경",
                REQUESTED_AT
            )
        );
        return new Fixture(
            admin,
            region.getRegionId(),
            content.getContentId(),
            request.getContentWithdrawalRequestId(),
            requester.getUserId()
        );
    }

    private Region saveRegion(String suffix) {
        return regionRepository.saveAndFlush(new Region(
            "WDR-" + suffix,
            "테스트 지역",
            true
        ));
    }

    private AppUser saveRegionAdmin(String suffix, Region region, AppUserStatus status) {
        AppUser admin = saveUser(suffix, "지역 관리자", status);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            admin,
            UserRole.REGION_ADMIN,
            region
        ));
        return admin;
    }

    private AppUser saveUser(String suffix, String name, AppUserStatus status) {
        return appUserRepository.saveAndFlush(new AppUser(
            suffix.toLowerCase() + "@example.com",
            "hashed-password",
            name,
            "010-1234-5678",
            status
        ));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, user.getUserId());
    }

    private DatabaseSnapshot snapshot(Fixture fixture) {
        entityManager.flush();
        entityManager.clear();
        return new DatabaseSnapshot(
            contentRepository.count(),
            withdrawalRequestRepository.count(),
            auditEventRepository.count(),
            contentState(fixture.contentId()),
            requestState(fixture.requestId())
        );
    }

    private void assertUnchanged(DatabaseSnapshot before, Fixture fixture) {
        assertThat(snapshot(fixture)).isEqualTo(before);
    }

    private ContentState contentState(Long contentId) {
        return jdbcTemplate.queryForObject(
            "SELECT status, deleted_at, version_no, updated_at FROM content WHERE content_id = ?",
            (resultSet, rowNum) -> new ContentState(
                resultSet.getString("status"),
                toInstant(resultSet.getTimestamp("deleted_at")),
                resultSet.getInt("version_no"),
                resultSet.getTimestamp("updated_at").toInstant()
            ),
            contentId
        );
    }

    private RequestState requestState(Long requestId) {
        return jdbcTemplate.queryForObject(
            """
                SELECT status, requested_by_user_id, reviewed_at, reviewed_by_user_id,
                       rejection_reason, invalidated_at, invalidated_by_user_id, invalidation_reason
                FROM content_withdrawal_request
                WHERE content_withdrawal_request_id = ?
                """,
            (resultSet, rowNum) -> new RequestState(
                resultSet.getString("status"),
                resultSet.getObject("requested_by_user_id", Long.class),
                toInstant(resultSet.getTimestamp("reviewed_at")),
                resultSet.getObject("reviewed_by_user_id", Long.class),
                resultSet.getString("rejection_reason"),
                toInstant(resultSet.getTimestamp("invalidated_at")),
                resultSet.getObject("invalidated_by_user_id", Long.class),
                resultSet.getString("invalidation_reason")
            ),
            requestId
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record Fixture(
        AppUser admin,
        Long regionId,
        Long contentId,
        Long requestId,
        Long requesterId
    ) {
    }

    private record DatabaseSnapshot(
        long contentCount,
        long requestCount,
        long auditCount,
        ContentState content,
        RequestState request
    ) {
    }

    private record ContentState(
        String status,
        Instant deletedAt,
        int versionNo,
        Instant updatedAt
    ) {
    }

    private record RequestState(
        String status,
        Long requestedByUserId,
        Instant reviewedAt,
        Long reviewedByUserId,
        String rejectionReason,
        Instant invalidatedAt,
        Long invalidatedByUserId,
        String invalidationReason
    ) {
    }
}
