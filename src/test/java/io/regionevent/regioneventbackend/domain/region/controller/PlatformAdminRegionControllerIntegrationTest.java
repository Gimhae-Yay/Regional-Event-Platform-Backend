package io.regionevent.regioneventbackend.domain.region.controller;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlatformAdminRegionControllerIntegrationTest {

    private final MockMvc mockMvc;
    private final AppUserRepository appUserRepository;
    private final PlatformAdminAssignmentRepository platformAdminAssignmentRepository;
    private final RegionRepository regionRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final AuditEventRepository auditEventRepository;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final EntityManager entityManager;

    @Autowired
    PlatformAdminRegionControllerIntegrationTest(
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
    void 전체관리자는_비공개지역을포함해_활성일반지역관리자수와함께조회한다() throws Exception {
        Fixture fixture = createFixture();
        Long privateRegionId = fixture.privateRegion().getRegionId();
        entityManager.flush();
        entityManager.clear();
        Instant privateRegionUpdatedAt = regionRepository.findById(privateRegionId)
            .orElseThrow()
            .getUpdatedAt();
        long auditEventCount = auditEventRepository.count();

        mockMvc.perform(authenticated(get("/api/v1/platform-admin/regions"), fixture.platformAdmin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.regions[0].regionId")
                .value(fixture.privateRegion().getRegionId().toString()))
            .andExpect(jsonPath("$.data.regions[0].isPublic").value(false))
            .andExpect(jsonPath("$.data.regions[0].regionAdminCount").value(2))
            .andExpect(jsonPath("$.data.regions[1].regionId")
                .value(fixture.publicRegion().getRegionId().toString()))
            .andExpect(jsonPath("$.data.regions[1].isPublic").value(true))
            .andExpect(jsonPath("$.data.regions[1].regionAdminCount").value(0));

        entityManager.flush();
        entityManager.clear();
        assertThat(regionRepository.findById(privateRegionId))
            .hasValueSatisfying(region ->
                assertThat(region.getUpdatedAt()).isEqualTo(privateRegionUpdatedAt)
            );
        assertThat(userRoleAssignmentRepository.findById(fixture.revokedAssignmentId()))
            .hasValueSatisfying(assignment ->
                assertThat(assignment.getStatus()).isEqualTo(UserRoleAssignmentStatus.REVOKED)
            );
        assertThat(auditEventRepository.count()).isEqualTo(auditEventCount);
    }

    @Test
    void 전체관리자가아닌사용자는_전체지역을조회할수없다() throws Exception {
        AppUser ordinaryUser = saveUser("ordinary", AppUserAccountKind.ORDINARY, AppUserStatus.ACTIVE);

        mockMvc.perform(authenticated(get("/api/v1/platform-admin/regions"), ordinaryUser))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 전체관리자는_공개여부필터로_비공개지역만조회할수있다() throws Exception {
        Fixture fixture = createFixture();

        mockMvc.perform(authenticated(get("/api/v1/platform-admin/regions")
                .queryParam("isPublic", "false"), fixture.platformAdmin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.regions.length()").value(1))
            .andExpect(jsonPath("$.data.regions[0].regionId")
                .value(fixture.privateRegion().getRegionId().toString()));
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        AppUser platformAdmin = saveUser(
            "platform-admin-" + suffix,
            AppUserAccountKind.PRIVILEGED,
            AppUserStatus.ACTIVE
        );
        platformAdminAssignmentRepository.saveAndFlush(new PlatformAdminAssignment(
            platformAdmin,
            PlatformAdminGrade.PLATFORM_ADMIN
        ));
        Region privateRegion = regionRepository.saveAndFlush(new Region(
            "PRIVATE-" + suffix,
            "가나다",
            false
        ));
        Region publicRegion = regionRepository.saveAndFlush(new Region(
            "PUBLIC-" + suffix,
            "라마바사",
            true
        ));
        assignRegionAdmin(privateRegion, "active-one-" + suffix, AppUserStatus.ACTIVE);
        assignRegionAdmin(privateRegion, "active-two-" + suffix, AppUserStatus.ACTIVE);
        UserRoleAssignment revokedAssignment = assignRegionAdmin(
            privateRegion,
            "revoked-" + suffix,
            AppUserStatus.ACTIVE
        );
        revokedAssignment.revoke(Instant.parse("2026-08-09T02:00:00Z"), "TEST_REVOKE");
        userRoleAssignmentRepository.saveAndFlush(revokedAssignment);
        assignRegionAdmin(privateRegion, "withdrawing-" + suffix, AppUserStatus.WITHDRAWING);

        return new Fixture(
            platformAdmin,
            privateRegion,
            publicRegion,
            revokedAssignment.getRoleAssignmentId()
        );
    }

    private UserRoleAssignment assignRegionAdmin(
        Region region,
        String loginIdentifier,
        AppUserStatus status
    ) {
        AppUser user = saveUser(loginIdentifier, AppUserAccountKind.ORDINARY, status);
        return userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            user,
            UserRole.REGION_ADMIN,
            region
        ));
    }

    private AppUser saveUser(
        String loginIdentifier,
        AppUserAccountKind accountKind,
        AppUserStatus status
    ) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            accountKind,
            status
        ));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
        AppUser user
    ) {
        return request.header("Authorization", "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, user.getUserId()));
    }

    private record Fixture(
        AppUser platformAdmin,
        Region privateRegion,
        Region publicRegion,
        Long revokedAssignmentId
    ) {
    }
}
