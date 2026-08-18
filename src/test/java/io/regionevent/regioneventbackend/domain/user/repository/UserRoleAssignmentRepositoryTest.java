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
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;

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
    void 단일_식별자로_방문자_역할을_저장하고_조회한다() {
        AppUser appUser = saveUser("visitor@example.com");
        UserRoleAssignment assignment = userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(appUser, UserRole.VISITOR, null)
        );
        entityManager.clear();

        UserRoleAssignment foundAssignment = userRoleAssignmentRepository.findById(
            assignment.getRoleAssignmentId()
        ).orElseThrow();

        assertThat(assignment.getGrantedAt()).isNotNull();
        assertThat(foundAssignment.getRoleAssignmentId()).isEqualTo(assignment.getRoleAssignmentId());
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
            operatorAssignment.getRoleAssignmentId()
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

    @Test
    void 활성_지역관리자_역할과_담당지역을_한번에_조회한다() {
        AppUser appUser = saveUser("region-admin@example.com");
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(appUser, UserRole.REGION_ADMIN, region)
        );
        entityManager.clear();

        UserRoleAssignment assignment = userRoleAssignmentRepository
            .findByAppUserUserIdAndRoleAndStatusAndAppUserStatus(
                appUser.getUserId(),
                UserRole.REGION_ADMIN,
                UserRoleAssignmentStatus.ACTIVE,
                AppUserStatus.ACTIVE
            )
            .orElseThrow();

        assertThat(assignment.getAppUser().getUserId()).isEqualTo(appUser.getUserId());
        assertThat(assignment.getRole()).isEqualTo(UserRole.REGION_ADMIN);
        assertThat(Hibernate.isInitialized(assignment.getRegion())).isTrue();
        assertThat(assignment.getRegion().getRegionId()).isEqualTo(region.getRegionId());
    }

    @Test
    void 존재하지_않는_회원은_지역관리자로_조회되지_않는다() {
        assertThat(userRoleAssignmentRepository.findByAppUserUserIdAndRoleAndStatusAndAppUserStatus(
                Long.MAX_VALUE,
                UserRole.REGION_ADMIN,
                UserRoleAssignmentStatus.ACTIVE,
                AppUserStatus.ACTIVE
            )).isEmpty();
    }

    @Test
    void 탈퇴_처리중인_회원은_지역관리자로_조회되지_않는다() {
        AppUser appUser = saveUser("withdrawing-admin@example.com", AppUserStatus.WITHDRAWING);
        Region region = regionRepository.saveAndFlush(new Region("DONGHAE", "동해시", true));
        userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(appUser, UserRole.REGION_ADMIN, region)
        );
        entityManager.clear();

        assertThat(userRoleAssignmentRepository.findByAppUserUserIdAndRoleAndStatusAndAppUserStatus(
            appUser.getUserId(),
            UserRole.REGION_ADMIN,
            UserRoleAssignmentStatus.ACTIVE,
            AppUserStatus.ACTIVE
        )).isEmpty();
    }

    @Test
    void 지역관리자_역할이_없는_회원은_조회되지_않는다() {
        AppUser appUser = saveUser("visitor-only@example.com");
        userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(appUser, UserRole.VISITOR, null)
        );
        entityManager.clear();

        assertThat(userRoleAssignmentRepository.findByAppUserUserIdAndRoleAndStatusAndAppUserStatus(
            appUser.getUserId(),
            UserRole.REGION_ADMIN,
            UserRoleAssignmentStatus.ACTIVE,
            AppUserStatus.ACTIVE
        )).isEmpty();
    }

    @Test
    void 활성_운영자_역할과_담당_지역을_함께_조회한다() {
        AppUser appUser = saveUser("active-operator@example.com");
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(appUser, UserRole.OPERATOR, region)
        );
        entityManager.clear();

        UserRoleAssignment assignment = userRoleAssignmentRepository
            .findByAppUserUserIdAndRoleAndStatusAndAppUserStatus(
                appUser.getUserId(),
                UserRole.OPERATOR,
                UserRoleAssignmentStatus.ACTIVE,
                AppUserStatus.ACTIVE
            )
            .orElseThrow();

        assertThat(assignment.getAppUser().getUserId()).isEqualTo(appUser.getUserId());
        assertThat(assignment.getRole()).isEqualTo(UserRole.OPERATOR);
        assertThat(Hibernate.isInitialized(assignment.getRegion())).isTrue();
        assertThat(assignment.getRegion().getRegionId()).isEqualTo(region.getRegionId());
    }

    @Test
    void 활성_회원에게_운영자_역할이_없으면_조회되지_않는다() {
        AppUser appUser = saveUser("active-visitor@example.com");
        userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(appUser, UserRole.VISITOR, null)
        );
        entityManager.clear();

        assertThat(userRoleAssignmentRepository.findByAppUserUserIdAndRoleAndStatusAndAppUserStatus(
            appUser.getUserId(),
            UserRole.OPERATOR,
            UserRoleAssignmentStatus.ACTIVE,
            AppUserStatus.ACTIVE
        )).isEmpty();
    }

    @Test
    void 비활성_운영자는_조회되지_않는다() {
        AppUser appUser = saveUser("withdrawing-operator@example.com", AppUserStatus.WITHDRAWING);
        Region region = regionRepository.saveAndFlush(new Region("DONGHAE", "동해시", true));
        userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(appUser, UserRole.OPERATOR, region)
        );
        entityManager.clear();

        assertThat(userRoleAssignmentRepository.findByAppUserUserIdAndRoleAndStatusAndAppUserStatus(
            appUser.getUserId(),
            UserRole.OPERATOR,
            UserRoleAssignmentStatus.ACTIVE,
            AppUserStatus.ACTIVE
        )).isEmpty();
    }

    @Test
    void 활성_방문자_조회는_일반계정만_반환한다() {
        AppUser ordinaryUser = saveUser("ordinary-visitor@example.com");
        AppUser privilegedUser = appUserRepository.saveAndFlush(new AppUser(
            "privileged-visitor@example.com",
            "hashed-password",
            "홍길동",
            "010-1234-5678",
            AppUserAccountKind.PRIVILEGED,
            AppUserStatus.ACTIVE
        ));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(ordinaryUser, UserRole.VISITOR, null));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(privilegedUser, UserRole.VISITOR, null));
        entityManager.clear();

        assertThat(userRoleAssignmentRepository.findActiveOrdinaryRoleAssignment(
            ordinaryUser.getUserId(),
            UserRole.VISITOR,
            UserRoleAssignmentStatus.ACTIVE,
            AppUserStatus.ACTIVE,
            AppUserAccountKind.ORDINARY
        )).isPresent();
        assertThat(userRoleAssignmentRepository.findActiveOrdinaryRoleAssignment(
            privilegedUser.getUserId(),
            UserRole.VISITOR,
            UserRoleAssignmentStatus.ACTIVE,
            AppUserStatus.ACTIVE,
            AppUserAccountKind.ORDINARY
        )).isEmpty();
    }

    private AppUser saveUser(String loginIdentifier) {
        return saveUser(loginIdentifier, AppUserStatus.ACTIVE);
    }

    private AppUser saveUser(
        String loginIdentifier,
        AppUserStatus status
    ) {
        return appUserRepository.saveAndFlush(
            new AppUser(loginIdentifier, "hashed-password", "홍길동", "010-1234-5678", status)
        );
    }
}
