package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentLogRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
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
class ContentHistoryControllerIntegrationTest {

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentLogRepository contentLogRepository;
    private final AuditEventRepository auditEventRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;

    @Autowired
    ContentHistoryControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentLogRepository contentLogRepository,
        AuditEventRepository auditEventRepository,
        JwtAccessTokenService jwtAccessTokenService,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentLogRepository = contentLogRepository;
        this.auditEventRepository = auditEventRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.entityManager = entityManager;
    }

    @Test
    void getContentHistory_returnsEveryStatusReasonAndActorRepresentation() throws Exception {
        Region region = saveRegion("GIMHAE");
        AppUser admin = saveUser("admin", "김해 지역 관리자", AppUserStatus.ACTIVE);
        assignRole(admin, UserRole.REGION_ADMIN, region);
        AppUser operator = saveUser("operator", "김해 행사 운영자", AppUserStatus.ACTIVE);
        AppUser withdrawnActor = saveUser("withdrawn", "탈퇴 예정 관리자", AppUserStatus.ACTIVE);
        Content content = saveContent(region, operator);
        Instant baseTime = Instant.parse("2026-08-01T00:00:00Z");

        saveLog(content, operator, ContentLogStatus.PENDING, null, baseTime);
        saveLog(content, admin, ContentLogStatus.REJECTED, "회차별 정원 정보를 확인할 수 없습니다.", baseTime.plusSeconds(1));
        ContentLog approved = saveLog(
            content,
            withdrawnActor,
            ContentLogStatus.APPROVED,
            null,
            baseTime.plusSeconds(2)
        );
        approved.unlinkActor();
        contentLogRepository.flush();
        saveLog(content, null, ContentLogStatus.PUBLISHED, null, baseTime.plusSeconds(3));
        saveLog(content, admin, ContentLogStatus.SUSPENDED, "안전 점검이 필요합니다.", baseTime.plusSeconds(4));
        saveLog(content, admin, ContentLogStatus.WITHDRAWN, "운영자가 철회를 요청했습니다.", baseTime.plusSeconds(5));
        saveLog(content, null, ContentLogStatus.ENDED, null, baseTime.plusSeconds(6));
        saveLog(content, admin, ContentLogStatus.DELETED, "등록 요청을 철회했습니다.", baseTime.plusSeconds(7));
        entityManager.clear();

        performGet(admin, content.getContentId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 이력 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(content.getContentId()))
            .andExpect(jsonPath("$.data.histories.length()").value(8))
            .andExpect(jsonPath("$.data.histories[0].status").value("PENDING"))
            .andExpect(jsonPath("$.data.histories[0].actor.userId").value(operator.getUserId()))
            .andExpect(jsonPath("$.data.histories[0].actor.displayName").value("김해 행사 운영자"))
            .andExpect(jsonPath("$.data.histories[1].status").value("REJECTED"))
            .andExpect(jsonPath("$.data.histories[1].reason").value("회차별 정원 정보를 확인할 수 없습니다."))
            .andExpect(jsonPath("$.data.histories[2].status").value("APPROVED"))
            .andExpect(jsonPath("$.data.histories[2].actor.userId").isEmpty())
            .andExpect(jsonPath("$.data.histories[2].actor.displayName").value("탈퇴한 사용자"))
            .andExpect(jsonPath("$.data.histories[3].status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.histories[3].actor").isEmpty())
            .andExpect(jsonPath("$.data.histories[4].status").value("SUSPENDED"))
            .andExpect(jsonPath("$.data.histories[4].reason").value("안전 점검이 필요합니다."))
            .andExpect(jsonPath("$.data.histories[5].status").value("WITHDRAWN"))
            .andExpect(jsonPath("$.data.histories[5].reason").value("운영자가 철회를 요청했습니다."))
            .andExpect(jsonPath("$.data.histories[6].status").value("ENDED"))
            .andExpect(jsonPath("$.data.histories[6].actor").isEmpty())
            .andExpect(jsonPath("$.data.histories[7].status").value("DELETED"))
            .andExpect(jsonPath("$.data.histories[7].reason").value("등록 요청을 철회했습니다."))
            .andExpect(jsonPath("$.data.histories[7].processedAt").value("2026-08-01T00:00:07Z"));

        assertThat(contentLogRepository.count()).isEqualTo(8);
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void getContentHistory_whenHistoryIsEmpty_returnsEmptyArrayWithoutChanges() throws Exception {
        Region region = saveRegion("EMPTY");
        AppUser admin = saveUser("empty-admin", "빈 이력 관리자", AppUserStatus.ACTIVE);
        assignRole(admin, UserRole.REGION_ADMIN, region);
        Content content = saveContent(region, saveUser("empty-operator", "빈 이력 운영자", AppUserStatus.ACTIVE));
        long contentCount = contentRepository.count();

        performGet(admin, content.getContentId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contentId").value(content.getContentId()))
            .andExpect(jsonPath("$.data.histories").isArray())
            .andExpect(jsonPath("$.data.histories").isEmpty());

        assertThat(contentRepository.count()).isEqualTo(contentCount);
        assertThat(contentLogRepository.count()).isZero();
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void getContentHistory_whenContentIsSoftDeleted_returnsDeletedHistory() throws Exception {
        Region region = saveRegion("DELETED");
        AppUser admin = saveUser("deleted-admin", "삭제 이력 관리자", AppUserStatus.ACTIVE);
        assignRole(admin, UserRole.REGION_ADMIN, region);
        Content content = saveContent(region, saveUser("deleted-operator", "삭제 운영자", AppUserStatus.ACTIVE));
        content.softDelete();
        contentRepository.flush();
        saveLog(
            content,
            admin,
            ContentLogStatus.DELETED,
            "등록 요청을 철회했습니다.",
            Instant.parse("2026-08-01T01:00:00Z")
        );
        entityManager.clear();

        performGet(admin, content.getContentId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.histories.length()").value(1))
            .andExpect(jsonPath("$.data.histories[0].status").value("DELETED"))
            .andExpect(jsonPath("$.data.histories[0].reason").value("등록 요청을 철회했습니다."));

        assertThat(contentRepository.findById(content.getContentId()).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(contentLogRepository.count()).isEqualTo(1);
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void getContentHistory_whenAdminRegionDiffers_returnsForbidden() throws Exception {
        Region assignedRegion = saveRegion("ASSIGNED");
        Region targetRegion = saveRegion("TARGET");
        AppUser admin = saveUser("other-region-admin", "타 지역 관리자", AppUserStatus.ACTIVE);
        assignRole(admin, UserRole.REGION_ADMIN, assignedRegion);
        Content content = saveContent(targetRegion, saveUser("target-operator", "대상 운영자", AppUserStatus.ACTIVE));

        performGet(admin, content.getContentId())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value(403))
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.data").isEmpty());

        assertThat(contentLogRepository.count()).isZero();
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void getContentHistory_whenUserIsNotRegionAdmin_returnsForbidden() throws Exception {
        Region region = saveRegion("NON_ADMIN");
        AppUser visitor = saveUser("visitor", "방문자", AppUserStatus.ACTIVE);
        assignRole(visitor, UserRole.VISITOR, null);
        Content content = saveContent(region, saveUser("non-admin-operator", "운영자", AppUserStatus.ACTIVE));

        performGet(visitor, content.getContentId())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getContentHistory_withoutAuthentication_returnsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/region-admin/contents/1/history")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void getContentHistory_whenContentDoesNotExist_returnsNotFound() throws Exception {
        Region region = saveRegion("NOT_FOUND");
        AppUser admin = saveUser("not-found-admin", "조회 관리자", AppUserStatus.ACTIVE);
        assignRole(admin, UserRole.REGION_ADMIN, region);

        performGet(admin, 9_999_999L)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value(404))
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."));
    }

    @Test
    void getContentHistory_whenContentIdIsInvalid_returnsInputErrors() throws Exception {
        AppUser user = saveUser("invalid-id-user", "입력 검증 사용자", AppUserStatus.ACTIVE);
        String authorization = bearerToken(user);

        mockMvc.perform(get("/api/v1/region-admin/contents/0/history")
                .header("Authorization", authorization)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/v1/region-admin/contents/not-a-number/history")
                .header("Authorization", authorization)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        mockMvc.perform(get("/api/v1/region-admin/contents/01/history")
                .header("Authorization", authorization)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/v1/region-admin/contents/+1/history")
                .header("Authorization", authorization)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/v1/region-admin/contents/9223372036854775808/history")
                .header("Authorization", authorization)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    private ResultActions performGet(AppUser user, Long contentId) throws Exception {
        return mockMvc.perform(get("/api/v1/region-admin/contents/{contentId}/history", contentId)
            .header("Authorization", bearerToken(user))
            .accept(MediaType.APPLICATION_JSON));
    }

    private Region saveRegion(String codePrefix) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return regionRepository.saveAndFlush(new Region(codePrefix + suffix, codePrefix + " 지역", true));
    }

    private AppUser saveUser(String identifierPrefix, String name, AppUserStatus status) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            identifierPrefix + suffix + "@example.com",
            "hashed-password",
            name,
            "010-1234-5678",
            status
        ));
    }

    private void assignRole(AppUser user, UserRole role, Region region) {
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, role, region));
    }

    private Content saveContent(Region region, AppUser operator) {
        return contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PENDING,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            Instant.parse("2026-08-02T00:00:00Z")
        ));
    }

    private ContentLog saveLog(
        Content content,
        AppUser actor,
        ContentLogStatus status,
        String reason,
        Instant processedAt
    ) {
        return contentLogRepository.saveAndFlush(new ContentLog(
            content,
            actor,
            status,
            reason,
            processedAt
        ));
    }

    private String bearerToken(AppUser user) {
        return "Bearer " + jwtAccessTokenService.issue(user.getUserId());
    }
}
