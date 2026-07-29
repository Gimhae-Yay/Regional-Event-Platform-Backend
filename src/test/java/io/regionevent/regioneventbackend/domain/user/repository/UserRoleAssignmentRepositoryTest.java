package io.regionevent.regioneventbackend.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;

import org.hibernate.Hibernate;
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
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentId;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=validate"
})
class UserRoleAssignmentRepositoryTest {

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final AppUserRepository appUserRepository;
    private final RegionRepository regionRepository;
    private final EntityManager entityManager;

    @Autowired
    UserRoleAssignmentRepositoryTest(
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
    void 복합키로_방문자_역할을_저장하고_조회한다() {
        AppUser appUser = saveUser("visitor@example.com");
        UserRoleAssignment assignment = userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(appUser, UserRole.VISITOR, null)
        );
        entityManager.clear();

        UserRoleAssignment foundAssignment = userRoleAssignmentRepository.findById(
            new UserRoleAssignmentId(appUser.getUserId(), UserRole.VISITOR)
        ).orElseThrow();

        assertThat(assignment.getGrantedAt()).isNotNull();
        assertThat(foundAssignment.getId().getUserId()).isEqualTo(appUser.getUserId());
        assertThat(foundAssignment.getRole()).isEqualTo(UserRole.VISITOR);
        assertThat(foundAssignment.getRegion()).isNull();
        assertThat(Hibernate.isInitialized(foundAssignment.getAppUser())).isFalse();
    }

    @Test
    void 운영자와_지역관리자는_지역과_함께_저장된다() {
        AppUser appUser = saveUser("operator@example.com");
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));

        UserRoleAssignment operatorAssignment = userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(appUser, UserRole.OPERATOR, region)
        );
        entityManager.clear();
        operatorAssignment = userRoleAssignmentRepository.findById(
            new UserRoleAssignmentId(appUser.getUserId(), UserRole.OPERATOR)
        ).orElseThrow();

        assertThat(operatorAssignment.getRole()).isEqualTo(UserRole.OPERATOR);
        assertThat(Hibernate.isInitialized(operatorAssignment.getRegion())).isFalse();
        assertThat(operatorAssignment.getRegion().getRegionId()).isEqualTo(region.getRegionId());
    }

    @Test
    void 같은_사용자의_같은_역할은_중복될_수_없다() {
        AppUser appUser = saveUser("duplicate@example.com");
        userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(appUser, UserRole.VISITOR, null)
        );

        assertThatThrownBy(
            () -> userRoleAssignmentRepository.saveAndFlush(
                new UserRoleAssignment(appUser, UserRole.VISITOR, null)
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 방문자는_지역을_가질_수_없다() {
        AppUser appUser = saveUser("visitor-region@example.com");
        Region region = regionRepository.saveAndFlush(new Region("DONGHAE", "동해시", true));

        assertThatThrownBy(
            () -> new UserRoleAssignment(appUser, UserRole.VISITOR, region)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 운영자와_지역관리자는_지역이_필수다() {
        AppUser appUser = saveUser("required-region@example.com");

        assertThatThrownBy(
            () -> new UserRoleAssignment(appUser, UserRole.OPERATOR, null)
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
            () -> new UserRoleAssignment(appUser, UserRole.REGION_ADMIN, null)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(
            new AppUser(loginIdentifier, "hashed-password", "홍길동", "010-1234-5678", AppUserStatus.ACTIVE)
        );
    }
}
