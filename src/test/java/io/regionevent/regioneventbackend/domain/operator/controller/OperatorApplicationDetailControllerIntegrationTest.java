package io.regionevent.regioneventbackend.domain.operator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;
import io.regionevent.regioneventbackend.domain.operator.repository.OperatorApplicationRepository;
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
class OperatorApplicationDetailControllerIntegrationTest {

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final OperatorApplicationRepository operatorApplicationRepository;
    private final AuditEventRepository auditEventRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;

    @Autowired
    OperatorApplicationDetailControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        OperatorApplicationRepository operatorApplicationRepository,
        AuditEventRepository auditEventRepository,
        JwtAccessTokenService jwtAccessTokenService,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.operatorApplicationRepository = operatorApplicationRepository;
        this.auditEventRepository = auditEventRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.entityManager = entityManager;
    }

    @Test
    void 담당_지역_관리자는_사업자_정보를_캐시하지_않는_상세_응답으로_조회한다() throws Exception {
        Fixture fixture = createFixture();

        getDetail(fixture.admin(), fixture.application().getOperatorApplicationId().toString())
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("운영자 승인 요청 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.operatorApplicationId")
                .value(fixture.application().getOperatorApplicationId()))
            .andExpect(jsonPath("$.data.applicantUserId").value(fixture.applicant().getUserId()))
            .andExpect(jsonPath("$.data.requestedRegionId").value(fixture.region().getRegionId()))
            .andExpect(jsonPath("$.data.businessInformation").value("사업자등록번호 123-45-67890"))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.inspectedUserId").doesNotExist())
            .andExpect(jsonPath("$.data.rejectedReason").doesNotExist())
            .andExpect(jsonPath("$.data.requestedAt").isString())
            .andExpect(jsonPath("$.data.updatedAt").isString());

        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void 다른_지역의_신청은_존재를_숨긴다() throws Exception {
        Fixture fixture = createFixture();
        AppUser otherAdmin = saveUser("other-admin", AppUserStatus.ACTIVE);
        assignRole(otherAdmin, UserRole.REGION_ADMIN, saveRegion("OTHER"));

        getDetail(otherAdmin, fixture.application().getOperatorApplicationId().toString())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 지역_관리자_역할이_없거나_인증되지_않으면_조회할_수_없다() throws Exception {
        Fixture fixture = createFixture();
        AppUser visitor = saveUser("visitor", AppUserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/region-admin/operator-requests/{requestId}",
                fixture.application().getOperatorApplicationId()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        getDetail(visitor, fixture.application().getOperatorApplicationId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 취소된_신청은_제거된_개인정보를_null로_반환한다() throws Exception {
        Fixture fixture = createFixture();
        entityManager.createNativeQuery("""
            update operator_application
            set applicant_user_id = null,
                business_information = null,
                status = 'CANCELLED',
                inspected_user_id = null,
                rejected_reason = null
            where operator_application_id = :operatorApplicationId
            """)
            .setParameter("operatorApplicationId", fixture.application().getOperatorApplicationId())
            .executeUpdate();
        entityManager.clear();

        getDetail(fixture.admin(), fixture.application().getOperatorApplicationId().toString())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))
            .andExpect(jsonPath("$.data.applicantUserId").doesNotExist())
            .andExpect(jsonPath("$.data.businessInformation").doesNotExist())
            .andExpect(jsonPath("$.data.inspectedUserId").doesNotExist())
            .andExpect(jsonPath("$.data.rejectedReason").doesNotExist());
    }

    @Test
    void 신청_식별자_형식과_범위_오류는_계약된_오류로_반환한다() throws Exception {
        Fixture fixture = createFixture();

        for (String invalidInput : new String[]{"0", "-1", "01", "+1"}) {
            getDetail(fixture.admin(), invalidInput)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        for (String invalidType : new String[]{"not-a-number", "9223372036854775808"}) {
            getDetail(fixture.admin(), invalidType)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        }
    }

    private ResultActions getDetail(AppUser user, String applicationId) throws Exception {
        return mockMvc.perform(get("/api/v1/region-admin/operator-requests/{requestId}", applicationId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(user.getUserId())));
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = saveRegion("R" + suffix);
        AppUser admin = saveUser("admin-" + suffix, AppUserStatus.ACTIVE);
        assignRole(admin, UserRole.REGION_ADMIN, region);
        AppUser applicant = saveUser("applicant-" + suffix, AppUserStatus.ACTIVE);
        OperatorApplication application = operatorApplicationRepository.saveAndFlush(new OperatorApplication(
            applicant,
            region,
            "사업자등록번호 123-45-67890",
            OperatorApplicationStatus.PENDING,
            null,
            null
        ));
        return new Fixture(region, admin, applicant, application);
    }

    private Region saveRegion(String prefix) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return regionRepository.saveAndFlush(new Region(prefix + suffix, "테스트 지역", true));
    }

    private AppUser saveUser(String prefix, AppUserStatus status) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            prefix + suffix + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            status
        ));
    }

    private void assignRole(AppUser user, UserRole role, Region region) {
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(user, role, region));
    }

    private record Fixture(
        Region region,
        AppUser admin,
        AppUser applicant,
        OperatorApplication application
    ) {
    }
}
