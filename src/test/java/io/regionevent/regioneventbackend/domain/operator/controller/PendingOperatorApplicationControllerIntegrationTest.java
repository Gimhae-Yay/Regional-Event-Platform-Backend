package io.regionevent.regioneventbackend.domain.operator.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

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
class PendingOperatorApplicationControllerIntegrationTest {

    private static final String PENDING_OPERATOR_REQUESTS_PATH = "/api/v1/region-admin/operator-requests";

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final OperatorApplicationRepository operatorApplicationRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;

    @Autowired
    PendingOperatorApplicationControllerIntegrationTest(
        MockMvc mockMvc,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        OperatorApplicationRepository operatorApplicationRepository,
        JwtAccessTokenService jwtAccessTokenService,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.operatorApplicationRepository = operatorApplicationRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.entityManager = entityManager;
    }

    @Test
    void 담당_지역의_Pending_신청만_사업자_정보_없이_반환한다() throws Exception {
        Fixture fixture = createFixture();
        OperatorApplication application = createPendingApplication(fixture.region(), "신청자");
        Region otherRegion = saveRegion("OTHER");
        createPendingApplication(otherRegion, "다른지역신청자");

        getPendingApplications(fixture.admin(), "PENDING")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("운영자 승인 요청 대기 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.operatorRequests.length()").value(1))
            .andExpect(jsonPath("$.data.operatorRequests[0].operatorApplicationId")
                .value(application.getOperatorApplicationId()))
            .andExpect(jsonPath("$.data.operatorRequests[0].applicantUserId")
                .value(application.getApplicant().getUserId()))
            .andExpect(jsonPath("$.data.operatorRequests[0].requestedRegionId")
                .value(fixture.region().getRegionId()))
            .andExpect(jsonPath("$.data.operatorRequests[0].requestedAt").isString())
            .andExpect(jsonPath("$.data.operatorRequests[0].businessInformation").doesNotExist());
    }

    @Test
    void 대기_신청이_없으면_빈_배열을_반환한다() throws Exception {
        Fixture fixture = createFixture();

        getPendingApplications(fixture.admin(), "PENDING")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.operatorRequests").isArray())
            .andExpect(jsonPath("$.data.operatorRequests").isEmpty());
    }

    @Test
    void 같은_신청_시각이면_신청_식별자_오름차순으로_정렬한다() throws Exception {
        Fixture fixture = createFixture();
        OperatorApplication first = createPendingApplication(fixture.region(), "첫번째신청자");
        OperatorApplication second = createPendingApplication(fixture.region(), "두번째신청자");
        Instant requestedAt = Instant.parse("2026-08-03T00:00:00Z");
        updateCreatedAt(first.getOperatorApplicationId(), requestedAt);
        updateCreatedAt(second.getOperatorApplicationId(), requestedAt);
        entityManager.clear();

        getPendingApplications(fixture.admin(), "PENDING")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.operatorRequests[0].operatorApplicationId")
                .value(first.getOperatorApplicationId()))
            .andExpect(jsonPath("$.data.operatorRequests[1].operatorApplicationId")
                .value(second.getOperatorApplicationId()));
    }

    @Test
    void 지역_관리자_역할이_없거나_담당_지역이_없으면_조회할_수_없다() throws Exception {
        Fixture fixture = createFixture();
        AppUser operator = saveUser("operator", AppUserStatus.ACTIVE);
        assignRole(operator, UserRole.OPERATOR, fixture.region());
        AppUser unassignedUser = saveUser("unassigned", AppUserStatus.ACTIVE);

        getPendingApplications(operator, "PENDING")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        getPendingApplications(unassignedUser, "PENDING")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 잘못된_상태와_인증_누락은_계약된_오류로_반환한다() throws Exception {
        Fixture fixture = createFixture();

        getPendingApplications(fixture.admin(), "APPROVED")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get(PENDING_OPERATOR_REQUESTS_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(fixture.admin().getUserId())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get(PENDING_OPERATOR_REQUESTS_PATH))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private ResultActions getPendingApplications(AppUser user, String status) throws Exception {
        return mockMvc.perform(get(PENDING_OPERATOR_REQUESTS_PATH)
            .param("status", status)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(user.getUserId())));
    }

    private Fixture createFixture() {
        Region region = saveRegion("REGION");
        AppUser admin = saveUser("admin", AppUserStatus.ACTIVE);
        assignRole(admin, UserRole.REGION_ADMIN, region);
        return new Fixture(region, admin);
    }

    private OperatorApplication createPendingApplication(Region region, String prefix) {
        AppUser applicant = saveUser(prefix, AppUserStatus.ACTIVE);
        return operatorApplicationRepository.saveAndFlush(new OperatorApplication(
            applicant,
            region,
            "사업자등록번호 123-45-67890",
            OperatorApplicationStatus.PENDING,
            null,
            null
        ));
    }

    private void updateCreatedAt(Long operatorApplicationId, Instant createdAt) {
        entityManager.createNativeQuery("""
            update operator_application
            set created_at = :createdAt
            where operator_application_id = :operatorApplicationId
            """)
            .setParameter("createdAt", createdAt)
            .setParameter("operatorApplicationId", operatorApplicationId)
            .executeUpdate();
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
        AppUser admin
    ) {
    }
}
