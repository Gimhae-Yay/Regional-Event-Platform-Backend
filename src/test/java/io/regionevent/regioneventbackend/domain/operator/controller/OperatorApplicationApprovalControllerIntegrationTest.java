package io.regionevent.regioneventbackend.domain.operator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
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
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperatorApplicationApprovalControllerIntegrationTest {

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final OperatorApplicationRepository operatorApplicationRepository;
    private final AuditEventRepository auditEventRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;

    @Autowired
    OperatorApplicationApprovalControllerIntegrationTest(
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
    void 승인_시_운영자_역할과_감사를_함께_기록하고_재승인은_멱등이다() throws Exception {
        Fixture fixture = createFixture(OperatorApplicationStatus.PENDING);

        approve(fixture.admin(), fixture.application().getOperatorApplicationId().toString())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("운영자 승인에 성공했습니다."))
            .andExpect(jsonPath("$.data.operatorApplicationId").value(fixture.application().getOperatorApplicationId()))
            .andExpect(jsonPath("$.data.status").value("APPROVED"))
            .andExpect(jsonPath("$.data.operatorRole").value("OPERATOR"))
            .andExpect(jsonPath("$.data.assignedRegionId").value(fixture.region().getRegionId()))
            .andExpect(jsonPath("$.data.processedAt").isString())
            .andExpect(jsonPath("$.data.businessInformation").doesNotExist());

        entityManager.flush();
        entityManager.clear();
        OperatorApplication approved = operatorApplicationRepository
            .findById(fixture.application().getOperatorApplicationId())
            .orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(OperatorApplicationStatus.APPROVED);
        assertThat(approved.getInspectedUser().getUserId()).isEqualTo(fixture.admin().getUserId());
        assertThat(userRoleAssignmentRepository.findByAppUserUserIdAndRoleAndStatusAndAppUserStatus(
            fixture.applicant().getUserId(),
            UserRole.OPERATOR,
            UserRoleAssignmentStatus.ACTIVE,
            AppUserStatus.ACTIVE
        )).hasValueSatisfying(assignment ->
            assertThat(assignment.getRegion().getRegionId()).isEqualTo(fixture.region().getRegionId())
        );
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getTargetType()).isEqualTo(AuditEventTargetType.OPERATOR_APPLICATION);
            assertThat(event.getTargetId()).isEqualTo(approved.getOperatorApplicationId());
            assertThat(event.getPreviousState()).isEqualTo("PENDING");
            assertThat(event.getNextState()).isEqualTo("APPROVED");
            assertThat(event.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(event.getReasonCode()).isEqualTo("OPERATOR_APPLICATION_APPROVED");
            assertThat(event.getOccurredAt()).isEqualTo(approved.getUpdatedAt());
        });

        approve(fixture.admin(), fixture.application().getOperatorApplicationId().toString())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.processedAt").value(approved.getUpdatedAt().toString()));
        assertThat(auditEventRepository.count()).isEqualTo(1);
    }

    @Test
    void 다른_지역_관리자의_신청은_찾을_수_없다() throws Exception {
        Fixture fixture = createFixture(OperatorApplicationStatus.PENDING);
        AppUser otherAdmin = saveUser("other-admin", AppUserStatus.ACTIVE);
        assignRole(otherAdmin, UserRole.REGION_ADMIN, saveRegion("OTHER"));

        approve(otherAdmin, fixture.application().getOperatorApplicationId().toString())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(operatorApplicationRepository.findById(fixture.application().getOperatorApplicationId()))
            .hasValueSatisfying(application ->
                assertThat(application.getStatus()).isEqualTo(OperatorApplicationStatus.PENDING)
            );
    }

    @Test
    void 지역_관리자가_아니면_승인할_수_없다() throws Exception {
        Fixture fixture = createFixture(OperatorApplicationStatus.PENDING);
        AppUser visitor = saveUser("visitor", AppUserStatus.ACTIVE);

        approve(visitor, fixture.application().getOperatorApplicationId().toString())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 종결된_신청은_승인할_수_없다() throws Exception {
        for (OperatorApplicationStatus applicationStatus : new OperatorApplicationStatus[]{
            OperatorApplicationStatus.REJECTED,
            OperatorApplicationStatus.CANCELLED
        }) {
            Fixture fixture = createFixture(applicationStatus);

            approve(fixture.admin(), fixture.application().getOperatorApplicationId().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATOR_APPLICATION_STATE_CONFLICT"));
        }
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void 잘못된_신청_식별자는_입력_계약에_따라_거부한다() throws Exception {
        Fixture fixture = createFixture(OperatorApplicationStatus.PENDING);

        for (String invalidInput : new String[]{"0", "-1", "01", "+1"}) {
            approve(fixture.admin(), invalidInput)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        for (String invalidType : new String[]{"not-a-number", "9223372036854775808"}) {
            approve(fixture.admin(), invalidType)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        }
    }

    @Test
    void 인증되지_않은_요청은_거부한다() throws Exception {
        mockMvc.perform(post("/api/v1/region-admin/operator-requests/1/approve"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private ResultActions approve(AppUser user, String applicationId) throws Exception {
        return mockMvc.perform(post(
            "/api/v1/region-admin/operator-requests/{applicationId}/approve",
            applicationId
        ).header("Authorization", "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, user.getUserId())));
    }

    private Fixture createFixture(OperatorApplicationStatus status) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = saveRegion("R" + suffix);
        AppUser admin = saveUser("admin-" + suffix, AppUserStatus.ACTIVE);
        assignRole(admin, UserRole.REGION_ADMIN, region);
        AppUser applicant = saveUser("applicant-" + suffix, AppUserStatus.ACTIVE);
        AppUser inspector = status == OperatorApplicationStatus.REJECTED
            ? saveUser("inspector-" + suffix, AppUserStatus.ACTIVE)
            : null;
        OperatorApplication application = operatorApplicationRepository.saveAndFlush(new OperatorApplication(
            applicant,
            region,
            "사업자 정보",
            status,
            inspector,
            status == OperatorApplicationStatus.REJECTED ? "사업자 정보 미비" : null
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
