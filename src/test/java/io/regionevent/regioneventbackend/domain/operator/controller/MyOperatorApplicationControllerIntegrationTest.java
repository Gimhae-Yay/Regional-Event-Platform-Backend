package io.regionevent.regioneventbackend.domain.operator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
@ExtendWith(OutputCaptureExtension.class)
class MyOperatorApplicationControllerIntegrationTest {

    private static final String PATH = "/api/v1/me/operator-application";
    private static final Instant TIED_CREATED_AT = Instant.parse("2026-08-20T01:15:30Z");

    private final MockMvc mockMvc;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RegionRepository regionRepository;
    private final OperatorApplicationRepository operatorApplicationRepository;
    private final AuditEventRepository auditEventRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    @Autowired
    MyOperatorApplicationControllerIntegrationTest(
        MockMvc mockMvc,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        RegionRepository regionRepository,
        OperatorApplicationRepository operatorApplicationRepository,
        AuditEventRepository auditEventRepository,
        JwtAccessTokenService jwtAccessTokenService,
        JdbcTemplate jdbcTemplate,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.regionRepository = regionRepository;
        this.operatorApplicationRepository = operatorApplicationRepository;
        this.auditEventRepository = auditEventRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    @Test
    void 신청이없는_활성회원은_빈권한토큰으로_명시적_null을조회한다() throws Exception {
        AppUser applicant = saveUser("empty", AppUserStatus.ACTIVE, "신청자 이름", "010-1111-1111");
        UserRoleAssignment assignment = userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(applicant, UserRole.VISITOR, null)
        );
        long roleCount = userRoleAssignmentRepository.count();
        long auditCount = auditEventRepository.count();

        performGet(applicant)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.operatorApplication").value(nullValue()));

        entityManager.flush();
        entityManager.clear();
        assertThat(userRoleAssignmentRepository.count()).isEqualTo(roleCount);
        assertThat(userRoleAssignmentRepository.findById(assignment.getRoleAssignmentId()))
            .hasValueSatisfying(current -> {
                assertThat(current.getRole()).isEqualTo(UserRole.VISITOR);
                assertThat(current.getStatus()).isEqualTo(assignment.getStatus());
            });
        assertThat(auditEventRepository.count()).isEqualTo(auditCount);
    }

    @Test
    void 동일한_생성시각이면_큰ID의_최신신청을_민감정보없이_조회하고_데이터를_변경하지않는다(
        CapturedOutput output
    ) throws Exception {
        Region region = saveRegion();
        AppUser applicant = saveUser("latest", AppUserStatus.ACTIVE, "민감 신청자", "010-2222-2222");
        AppUser inspector = saveUser("inspector", AppUserStatus.ACTIVE, "민감 심사자", "010-3333-3333");
        UserRoleAssignment assignment = userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(applicant, UserRole.VISITOR, null)
        );
        OperatorApplication lowerId = operatorApplicationRepository.saveAndFlush(new OperatorApplication(
            applicant,
            region,
            "비공개 사업자 정보 A",
            OperatorApplicationStatus.REJECTED,
            inspector,
            "이전 반려 사유"
        ));
        OperatorApplication higherId = operatorApplicationRepository.saveAndFlush(new OperatorApplication(
            applicant,
            region,
            "비공개 사업자 정보 B",
            OperatorApplicationStatus.APPROVED,
            inspector,
            null
        ));
        updateCreatedAt(lowerId.getOperatorApplicationId(), TIED_CREATED_AT);
        updateCreatedAt(higherId.getOperatorApplicationId(), TIED_CREATED_AT);
        entityManager.clear();
        List<ApplicationState> applicationStates = operatorApplicationRepository.findAll().stream()
            .map(ApplicationState::from)
            .toList();
        RegionState regionState = RegionState.from(regionRepository.findById(region.getRegionId()).orElseThrow());
        long roleCount = userRoleAssignmentRepository.count();
        long auditCount = auditEventRepository.count();
        entityManager.clear();

        ResultActions result = performGet(applicant)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.operatorApplication.operatorApplicationId")
                .value(higherId.getOperatorApplicationId().toString()))
            .andExpect(jsonPath("$.data.operatorApplication.status").value("APPROVED"))
            .andExpect(jsonPath("$.data.operatorApplication.requestedRegionId")
                .value(region.getRegionId().toString()))
            .andExpect(jsonPath("$.data.operatorApplication.requestedRegionName").value("김해시"))
            .andExpect(jsonPath("$.data.operatorApplication.createdAt").value(TIED_CREATED_AT.toString()))
            .andExpect(jsonPath("$.data.operatorApplication.reviewedAt").isString())
            .andExpect(jsonPath("$.data.operatorApplication.rejectedReason").value(nullValue()));

        String responseBody = result.andReturn().getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain(
            "비공개 사업자 정보 A",
            "비공개 사업자 정보 B",
            "이전 반려 사유",
            applicant.getLoginIdentifier(),
            applicant.getName(),
            applicant.getPhone(),
            inspector.getLoginIdentifier(),
            inspector.getName(),
            inspector.getPhone(),
            "businessInformation",
            "inspectedUser",
            "applicant"
        );
        assertThat(output.getOut())
            .contains("HTTP request completed. method=GET, uri=" + PATH + ", status=200")
            .doesNotContain(
                "비공개 사업자 정보 A",
                "비공개 사업자 정보 B",
                applicant.getLoginIdentifier(),
                applicant.getName(),
                applicant.getPhone(),
                inspector.getLoginIdentifier(),
                inspector.getName(),
                inspector.getPhone()
            );

        entityManager.flush();
        entityManager.clear();
        assertThat(operatorApplicationRepository.findAll().stream().map(ApplicationState::from).toList())
            .containsExactlyInAnyOrderElementsOf(applicationStates);
        assertThat(RegionState.from(regionRepository.findById(region.getRegionId()).orElseThrow()))
            .isEqualTo(regionState);
        assertThat(userRoleAssignmentRepository.count()).isEqualTo(roleCount);
        assertThat(userRoleAssignmentRepository.findById(assignment.getRoleAssignmentId()))
            .hasValueSatisfying(current -> assertThat(current.getStatus()).isEqualTo(assignment.getStatus()));
        assertThat(auditEventRepository.count()).isEqualTo(auditCount);
    }

    @Test
    void 미인증과_무효토큰은_401이고_비활성및삭제회원은_403이다() throws Exception {
        AppUser inactiveUser = saveUser("inactive", AppUserStatus.WITHDRAWING, "비활성 회원", "010-4444-4444");
        long userCount = appUserRepository.count();
        long applicationCount = operatorApplicationRepository.count();
        long roleCount = userRoleAssignmentRepository.count();
        long regionCount = regionRepository.count();
        long auditCount = auditEventRepository.count();

        mockMvc.perform(get(PATH))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer malformed"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        performGet(inactiveUser)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get(PATH).header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + jwtAccessTokenService.issue(Long.MAX_VALUE, List.of())
            ))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        entityManager.flush();
        entityManager.clear();
        assertThat(appUserRepository.count()).isEqualTo(userCount);
        assertThat(appUserRepository.findById(inactiveUser.getUserId()))
            .hasValueSatisfying(current -> assertThat(current.getStatus()).isEqualTo(AppUserStatus.WITHDRAWING));
        assertThat(operatorApplicationRepository.count()).isEqualTo(applicationCount);
        assertThat(userRoleAssignmentRepository.count()).isEqualTo(roleCount);
        assertThat(regionRepository.count()).isEqualTo(regionCount);
        assertThat(auditEventRepository.count()).isEqualTo(auditCount);
    }

    @Test
    void 연결된_최신신청이_CANCELLED이면_이전신청으로_fallback하지않는다() throws Exception {
        Region region = saveRegion();
        AppUser applicant = saveUser("cancelled", AppUserStatus.ACTIVE, "취소 신청자", "010-5555-5555");
        AppUser inspector = saveUser("cancelled-inspector", AppUserStatus.ACTIVE, "심사자", "010-6666-6666");
        OperatorApplication previous = operatorApplicationRepository.saveAndFlush(new OperatorApplication(
            applicant,
            region,
            "이전 신청",
            OperatorApplicationStatus.REJECTED,
            inspector,
            "이전 반려"
        ));
        OperatorApplication latest = operatorApplicationRepository.saveAndFlush(new OperatorApplication(
            applicant,
            region,
            "연결이 남은 취소 신청",
            OperatorApplicationStatus.CANCELLED,
            null,
            null
        ));
        updateCreatedAt(previous.getOperatorApplicationId(), TIED_CREATED_AT.minusSeconds(1));
        updateCreatedAt(latest.getOperatorApplicationId(), TIED_CREATED_AT);
        entityManager.clear();

        performGet(applicant)
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    private ResultActions performGet(AppUser user) throws Exception {
        return mockMvc.perform(get(PATH).header(
            HttpHeaders.AUTHORIZATION,
            "Bearer " + jwtAccessTokenService.issue(user.getUserId(), List.of())
        ));
    }

    private AppUser saveUser(
        String prefix,
        AppUserStatus status,
        String name,
        String phone
    ) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            prefix + '-' + suffix + "@example.com",
            "hashed-password",
            name,
            phone,
            status
        ));
    }

    private Region saveRegion() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
    }

    private void updateCreatedAt(Long operatorApplicationId, Instant createdAt) {
        jdbcTemplate.update(
            "UPDATE operator_application SET created_at = ? WHERE operator_application_id = ?",
            createdAt,
            operatorApplicationId
        );
    }

    private record ApplicationState(
        Long operatorApplicationId,
        Long applicantUserId,
        Long requestedRegionId,
        String businessInformation,
        OperatorApplicationStatus status,
        Long inspectedUserId,
        String rejectedReason,
        Instant createdAt,
        Instant updatedAt
    ) {

        private static ApplicationState from(OperatorApplication application) {
            return new ApplicationState(
                application.getOperatorApplicationId(),
                application.getApplicant() == null ? null : application.getApplicant().getUserId(),
                application.getRequestedRegion().getRegionId(),
                application.getBusinessInformation(),
                application.getStatus(),
                application.getInspectedUser() == null ? null : application.getInspectedUser().getUserId(),
                application.getRejectedReason(),
                application.getCreatedAt(),
                application.getUpdatedAt()
            );
        }
    }

    private record RegionState(
        Long regionId,
        String regionCode,
        String name,
        boolean isPublic,
        Instant createdAt,
        Instant updatedAt
    ) {

        private static RegionState from(Region region) {
            return new RegionState(
                region.getRegionId(),
                region.getRegionCode(),
                region.getName(),
                region.isPublic(),
                region.getCreatedAt(),
                region.getUpdatedAt()
            );
        }
    }
}
