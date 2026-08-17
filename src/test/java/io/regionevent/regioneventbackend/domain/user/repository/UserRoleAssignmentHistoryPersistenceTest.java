package io.regionevent.regioneventbackend.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class UserRoleAssignmentHistoryPersistenceTest {

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final AppUserRepository appUserRepository;
    private final RegionRepository regionRepository;
    private final EntityManager entityManager;

    @Autowired
    UserRoleAssignmentHistoryPersistenceTest(
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        AppUserRepository appUserRepository,
        RegionRepository regionRepository,
        EntityManager entityManager
    ) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.appUserRepository = appUserRepository;
        this.regionRepository = regionRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 회수한_지역관리자_배정은_이력으로_보존하고_새_활성_배정을_저장한다() {
        AppUser appUser = saveUser("reassigned-admin@example.com");
        Region previousRegion = saveRegion("GIMHAE", "김해시");
        Region nextRegion = saveRegion("DONGHAE", "동해시");
        UserRoleAssignment revokedAssignment = userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(appUser, UserRole.REGION_ADMIN, previousRegion)
        );

        Instant revokedAt = Instant.parse("2026-08-08T00:00:00Z");
        revokedAssignment.revoke(revokedAt, "REGION_ADMIN_REASSIGNMENT");
        userRoleAssignmentRepository.saveAndFlush(revokedAssignment);
        UserRoleAssignment activeAssignment = userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(appUser, UserRole.REGION_ADMIN, nextRegion)
        );
        entityManager.clear();

        UserRoleAssignment foundRevokedAssignment = userRoleAssignmentRepository.findById(
            revokedAssignment.getRoleAssignmentId()
        ).orElseThrow();
        UserRoleAssignment foundActiveAssignment = userRoleAssignmentRepository
            .findByAppUserUserIdAndRoleAndStatusAndAppUserStatus(
                appUser.getUserId(),
                UserRole.REGION_ADMIN,
                UserRoleAssignmentStatus.ACTIVE,
                AppUserStatus.ACTIVE
            )
            .orElseThrow();

        assertThat(foundRevokedAssignment.getStatus()).isEqualTo(UserRoleAssignmentStatus.REVOKED);
        assertThat(foundRevokedAssignment.getRevokedAt()).isEqualTo(revokedAt);
        assertThat(foundRevokedAssignment.getRevokeReasonCode())
            .isEqualTo("REGION_ADMIN_REASSIGNMENT");
        assertThat(foundActiveAssignment.getRoleAssignmentId())
            .isEqualTo(activeAssignment.getRoleAssignmentId());
        assertThat(foundActiveAssignment.getRegion().getRegionId()).isEqualTo(nextRegion.getRegionId());
    }

    @Test
    void 사용자는_하나의_활성_지역관리자_배정만_가질_수_있다() {
        AppUser appUser = saveUser("single-active-admin@example.com");
        Region firstRegion = saveRegion("GIMHAE", "김해시");
        Region secondRegion = saveRegion("DONGHAE", "동해시");
        userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(appUser, UserRole.REGION_ADMIN, firstRegion)
        );

        assertThatThrownBy(() -> userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(appUser, UserRole.REGION_ADMIN, secondRegion)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 활성_지역관리자_수를_지역별로_집계한다() {
        Region gimhae = saveRegion("GIMHAE", "김해시");
        Region donghae = saveRegion("DONGHAE", "동해시");
        UserRoleAssignment revokedAssignment = userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(saveUser("revoked-admin@example.com"), UserRole.REGION_ADMIN, gimhae)
        );
        userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(saveUser("active-admin@example.com"), UserRole.REGION_ADMIN, gimhae)
        );
        userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(saveUser("other-region-admin@example.com"), UserRole.REGION_ADMIN, donghae)
        );
        revokedAssignment.revoke(Instant.parse("2026-08-08T00:00:00Z"), "REGION_ADMIN_REVOCATION");
        userRoleAssignmentRepository.saveAndFlush(revokedAssignment);

        assertThat(userRoleAssignmentRepository.countActiveRegionAdminsByRegionRegionId(gimhae.getRegionId()))
            .isEqualTo(1);
        assertThat(userRoleAssignmentRepository.countActiveRegionAdminsByRegionRegionId(donghae.getRegionId()))
            .isEqualTo(1);
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(
            new AppUser(loginIdentifier, "hashed-password", "홍길동", "010-1234-5678", AppUserStatus.ACTIVE)
        );
    }

    private Region saveRegion(String regionCode, String name) {
        return regionRepository.saveAndFlush(new Region(regionCode, name, true));
    }
}
