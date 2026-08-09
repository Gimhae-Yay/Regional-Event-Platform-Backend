package io.regionevent.regioneventbackend.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlatformAdminUserRoleControllerIntegrationTest {

    private final MockMvc mockMvc;
    private final AppUserRepository appUserRepository;
    private final PlatformAdminAssignmentRepository platformAdminAssignmentRepository;
    private final RegionRepository regionRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final AuditEventRepository auditEventRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;

    @Autowired
    PlatformAdminUserRoleControllerIntegrationTest(
        MockMvc mockMvc,
        AppUserRepository appUserRepository,
        PlatformAdminAssignmentRepository platformAdminAssignmentRepository,
        RegionRepository regionRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        AuditEventRepository auditEventRepository,
        JwtAccessTokenService jwtAccessTokenService,
        EntityManager entityManager
    ) {
        this.mockMvc = mockMvc;
        this.appUserRepository = appUserRepository;
        this.platformAdminAssignmentRepository = platformAdminAssignmentRepository;
        this.regionRepository = regionRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.auditEventRepository = auditEventRepository;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.entityManager = entityManager;
    }

    @Test
    void 임명_무변경_재배정_회수는_역할_이력과_성공_감사를_함께_기록한다() throws Exception {
        Fixture fixture = createFixture();

        changeRole(
            fixture.actor(),
            fixture.targetUser().getUserId(),
            "REGION_ADMIN",
            fixture.firstRegion().getRegionId(),
            "REGION_ADMIN_APPOINTMENT",
            "OPS-2026-0809-001"
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.role").value("REGION_ADMIN"))
            .andExpect(jsonPath("$.data.regionId").value(fixture.firstRegion().getRegionId().toString()))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        entityManager.flush();
        entityManager.clear();
        UserRoleAssignment firstAssignment = findActiveAssignment(fixture.targetUser().getUserId());
        assertSuccessfulAudit(
            auditEvents(),
            firstAssignment.getRoleAssignmentId(),
            "REGION_ADMIN_APPOINTMENT",
            null,
            "ACTIVE"
        );

        changeRole(
            fixture.actor(),
            fixture.targetUser().getUserId(),
            "REGION_ADMIN",
            fixture.firstRegion().getRegionId(),
            "REGION_ADMIN_APPOINTMENT",
            "OPS-2026-0809-002"
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roleAssignmentId").value(firstAssignment.getRoleAssignmentId().toString()));

        assertThat(auditEventRepository.count()).isEqualTo(1);

        changeRole(
            fixture.actor(),
            fixture.targetUser().getUserId(),
            "REGION_ADMIN",
            fixture.secondRegion().getRegionId(),
            "REGION_ADMIN_APPOINTMENT",
            "OPS-2026-0809-003"
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.regionId").value(fixture.secondRegion().getRegionId().toString()));

        entityManager.flush();
        entityManager.clear();
        UserRoleAssignment reassigned = findActiveAssignment(fixture.targetUser().getUserId());
        assertThat(userRoleAssignmentRepository.findById(firstAssignment.getRoleAssignmentId()))
            .hasValueSatisfying(assignment -> {
                assertThat(assignment.getStatus()).isEqualTo(UserRoleAssignmentStatus.REVOKED);
                assertThat(assignment.getRevokeReasonCode()).isEqualTo("REGION_ADMIN_REASSIGNMENT");
            });
        assertSuccessfulAudit(
            auditEvents(),
            firstAssignment.getRoleAssignmentId(),
            "REGION_ADMIN_APPOINTMENT",
            "ACTIVE",
            "REVOKED"
        );
        assertSuccessfulAudit(
            auditEvents(),
            reassigned.getRoleAssignmentId(),
            "REGION_ADMIN_APPOINTMENT",
            null,
            "ACTIVE"
        );

        changeRole(
            fixture.actor(),
            fixture.targetUser().getUserId(),
            "NONE",
            null,
            "REGION_ADMIN_REVOCATION",
            "OPS-2026-0809-004"
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.role").doesNotExist())
            .andExpect(jsonPath("$.data.status").value("REVOKED"));

        entityManager.flush();
        entityManager.clear();
        assertThat(userRoleAssignmentRepository.findByAppUserUserIdAndRoleAndStatusAndAppUserStatus(
            fixture.targetUser().getUserId(),
            UserRole.REGION_ADMIN,
            UserRoleAssignmentStatus.ACTIVE,
            AppUserStatus.ACTIVE
        )).isEmpty();
        assertSuccessfulAudit(
            auditEvents(),
            reassigned.getRoleAssignmentId(),
            "REGION_ADMIN_REVOCATION",
            "ACTIVE",
            "REVOKED"
        );
    }

    @Test
    void 고권한_배정이_없는_사용자는_지역관리자_역할을_변경할_수_없다() throws Exception {
        Fixture fixture = createFixture();
        AppUser ordinaryUser = saveOrdinaryUser("ordinary");

        changeRole(
            ordinaryUser,
            fixture.targetUser().getUserId(),
            "REGION_ADMIN",
            fixture.firstRegion().getRegionId(),
            "REGION_ADMIN_APPOINTMENT",
            "OPS-2026-0809-005"
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(auditEventRepository.count()).isZero();
    }

    private ResultActions changeRole(
        AppUser actor,
        Long targetUserId,
        String role,
        Long regionId,
        String reasonCode,
        String evidenceReference
    ) throws Exception {
        String regionIdField = regionId == null ? "" : ",\n  \"regionId\": \"" + regionId + "\"";
        return mockMvc.perform(authenticated(patch("/api/v1/platform-admin/users/{userId}/role", targetUserId)
            .contentType("application/json")
            .content("""
                {
                  "role": "%s"%s,
                  "reasonCode": "%s",
                  "evidenceReference": "%s"
                }
                """.formatted(role, regionIdField, reasonCode, evidenceReference)), actor));
    }

    private UserRoleAssignment findActiveAssignment(Long userId) {
        return userRoleAssignmentRepository.findByAppUserUserIdAndRoleAndStatusAndAppUserStatus(
            userId,
            UserRole.REGION_ADMIN,
            UserRoleAssignmentStatus.ACTIVE,
            AppUserStatus.ACTIVE
        ).orElseThrow();
    }

    private List<AuditEvent> auditEvents() {
        return auditEventRepository.findAll();
    }

    private void assertSuccessfulAudit(
        List<AuditEvent> events,
        Long assignmentId,
        String reasonCode,
        String previousState,
        String nextState
    ) {
        assertThat(events).anySatisfy(event -> {
            assertThat(event.getTargetType()).isEqualTo(AuditEventTargetType.USER_ROLE_ASSIGNMENT);
            assertThat(event.getTargetId()).isEqualTo(assignmentId);
            assertThat(event.getResult()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(event.getReasonCode()).isEqualTo(reasonCode);
            assertThat(event.getPreviousState()).isEqualTo(previousState);
            assertThat(event.getNextState()).isEqualTo(nextState);
        });
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        AppUser actor = appUserRepository.saveAndFlush(new AppUser(
            "platform-admin-" + suffix + "@example.com",
            "hashed-password",
            "전체관리자",
            "010-1234-5678",
            AppUserAccountKind.PRIVILEGED,
            AppUserStatus.ACTIVE
        ));
        platformAdminAssignmentRepository.saveAndFlush(new PlatformAdminAssignment(
            actor,
            PlatformAdminGrade.PLATFORM_ADMIN
        ));
        AppUser targetUser = saveOrdinaryUser("target-" + suffix);
        Region firstRegion = regionRepository.saveAndFlush(new Region("A" + suffix, "첫 지역", true));
        Region secondRegion = regionRepository.saveAndFlush(new Region("B" + suffix, "둘째 지역", true));
        return new Fixture(actor, targetUser, firstRegion, secondRegion);
    }

    private AppUser saveOrdinaryUser(String prefix) {
        return appUserRepository.saveAndFlush(new AppUser(
            prefix + Long.toUnsignedString(System.nanoTime()) + "@example.com",
            "hashed-password",
            "사용자",
            "010-9876-5432",
            AppUserStatus.ACTIVE
        ));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
        AppUser user
    ) {
        return request.header("Authorization", "Bearer " + jwtAccessTokenService.issue(user.getUserId()));
    }

    private record Fixture(
        AppUser actor,
        AppUser targetUser,
        Region firstRegion,
        Region secondRegion
    ) {
    }
}
