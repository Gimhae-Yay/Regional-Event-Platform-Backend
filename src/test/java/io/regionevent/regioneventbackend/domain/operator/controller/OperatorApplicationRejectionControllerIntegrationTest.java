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
import org.springframework.http.MediaType;
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
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperatorApplicationRejectionControllerIntegrationTest {

    private final MockMvc mockMvc;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final OperatorApplicationRepository operatorApplicationRepository;
    private final AuditEventRepository auditEventRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;

    @Autowired
    OperatorApplicationRejectionControllerIntegrationTest(
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
    void 반려_시_감사를_함께_기록하고_재반려는_멱등이다() throws Exception {
        Fixture fixture = createFixture(OperatorApplicationStatus.PENDING);
        String rejectedReason = "사업자 정보가 부족합니다.";

        reject(fixture.admin(), fixture.application().getOperatorApplicationId().toString(), "  " + rejectedReason + "  ")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("운영자 신청 반려에 성공했습니다."))
            .andExpect(jsonPath("$.data.operatorApplicationId").value(fixture.application().getOperatorApplicationId()))
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andExpect(jsonPath("$.data.rejectedReason").value(rejectedReason))
            .andExpect(jsonPath("$.data.processedAt").isString())
            .andExpect(jsonPath("$.data.businessInformation").doesNotExist());

        entityManager.flush();
        entityManager.clear();
        OperatorApplication rejected = operatorApplicationRepository
            .findById(fixture.application().getOperatorApplicationId())
            .orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(OperatorApplicationStatus.REJECTED);
        assertThat(rejected.getRejectedReason()).isEqualTo(rejectedReason);
        assertThat(rejected.getInspectedUser().getUserId()).isEqualTo(fixture.admin().getUserId());
        assertThat(userRoleAssignmentRepository.findByAppUserUserIdAndRoleAndStatusAndAppUserStatus(
            fixture.applicant().getUserId(),
            UserRole.OPERATOR,
            UserRoleAssignmentStatus.ACTIVE,
            AppUserStatus.ACTIVE
        )).isEmpty();
        assertThat(auditEventRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getTargetType()).isEqualTo(AuditEventTargetType.OPERATOR_APPLICATION);
            assertThat(event.getTargetId()).isEqualTo(rejected.getOperatorApplicationId());
            assertThat(event.getPreviousState()).isEqualTo("PENDING");
            assertThat(event.getNextState()).isEqualTo("REJECTED");
            assertThat(event.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(event.getReasonCode()).isEqualTo("OPERATOR_APPLICATION_REJECTED");
            assertThat(event.getOccurredAt()).isEqualTo(rejected.getUpdatedAt());
        });

        reject(fixture.admin(), fixture.application().getOperatorApplicationId().toString(), "다른 사유")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rejectedReason").value(rejectedReason))
            .andExpect(jsonPath("$.data.processedAt").value(rejected.getUpdatedAt().toString()));
        assertThat(auditEventRepository.count()).isEqualTo(1);
    }

    @Test
    void 다른_지역_관리자의_신청은_찾을_수_없다() throws Exception {
        Fixture fixture = createFixture(OperatorApplicationStatus.PENDING);
        AppUser otherAdmin = saveUser("other-admin", AppUserStatus.ACTIVE);
        assignRole(otherAdmin, UserRole.REGION_ADMIN, saveRegion("OTHER"));

        reject(otherAdmin, fixture.application().getOperatorApplicationId().toString(), "사유")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(operatorApplicationRepository.findById(fixture.application().getOperatorApplicationId()))
            .hasValueSatisfying(application ->
                assertThat(application.getStatus()).isEqualTo(OperatorApplicationStatus.PENDING)
            );
    }

    @Test
    void 지역_관리자가_아니면_반려할_수_없다() throws Exception {
        Fixture fixture = createFixture(OperatorApplicationStatus.PENDING);
        AppUser visitor = saveUser("visitor", AppUserStatus.ACTIVE);

        reject(visitor, fixture.application().getOperatorApplicationId().toString(), "사유")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(operatorApplicationRepository.findById(fixture.application().getOperatorApplicationId()))
            .hasValueSatisfying(application ->
                assertThat(application.getStatus()).isEqualTo(OperatorApplicationStatus.PENDING)
            );
    }

    @Test
    void 승인되었거나_취소된_신청은_반려할_수_없다() throws Exception {
        for (OperatorApplicationStatus applicationStatus : new OperatorApplicationStatus[]{
            OperatorApplicationStatus.APPROVED,
            OperatorApplicationStatus.CANCELLED
        }) {
            Fixture fixture = createFixture(applicationStatus);

            reject(fixture.admin(), fixture.application().getOperatorApplicationId().toString(), "사유")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATOR_APPLICATION_STATE_CONFLICT"));
        }
        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void 잘못된_신청_식별자와_반려_사유는_입력_계약에_따라_거부한다() throws Exception {
        Fixture fixture = createFixture(OperatorApplicationStatus.PENDING);

        for (String invalidInput : new String[]{"0", "-1", "01", "+1"}) {
            reject(fixture.admin(), invalidInput, "사유")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        for (String invalidType : new String[]{"not-a-number", "9223372036854775808"}) {
            reject(fixture.admin(), invalidType, "사유")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        }
        rejectWithBody(fixture.admin(), fixture.application().getOperatorApplicationId().toString(), "{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        reject(fixture.admin(), fixture.application().getOperatorApplicationId().toString(), "   ")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        reject(fixture.admin(), fixture.application().getOperatorApplicationId().toString(), "가".repeat(2_001))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        rejectWithBody(fixture.admin(), fixture.application().getOperatorApplicationId().toString(), "{\"rejectedReason\":")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));

        assertThat(operatorApplicationRepository.findById(fixture.application().getOperatorApplicationId()))
            .hasValueSatisfying(application ->
                assertThat(application.getStatus()).isEqualTo(OperatorApplicationStatus.PENDING)
            );
    }

    @Test
    void 인증되지_않은_요청은_거부한다() throws Exception {
        mockMvc.perform(post("/api/v1/region-admin/operator-requests/1/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rejectedReason\":\"사유\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private ResultActions reject(AppUser user, String applicationId, String rejectedReason) throws Exception {
        return rejectWithBody(user, applicationId, "{\"rejectedReason\":\"" + rejectedReason + "\"}");
    }

    private ResultActions rejectWithBody(AppUser user, String applicationId, String body) throws Exception {
        return mockMvc.perform(post(
            "/api/v1/region-admin/operator-requests/{applicationId}/reject",
            applicationId
        ).header("Authorization", "Bearer " + jwtAccessTokenService.issue(user.getUserId()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private Fixture createFixture(OperatorApplicationStatus status) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = saveRegion("R" + suffix);
        AppUser admin = saveUser("admin-" + suffix, AppUserStatus.ACTIVE);
        assignRole(admin, UserRole.REGION_ADMIN, region);
        AppUser applicant = saveUser("applicant-" + suffix, AppUserStatus.ACTIVE);
        AppUser inspector = status == OperatorApplicationStatus.APPROVED
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
        return new Fixture(admin, applicant, application);
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
        AppUser admin,
        AppUser applicant,
        OperatorApplication application
    ) {
    }
}
