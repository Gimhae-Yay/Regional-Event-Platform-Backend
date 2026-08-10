package io.regionevent.regioneventbackend.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

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
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class AppUserRepositoryTest {

    private final AppUserRepository appUserRepository;
    private final RegionRepository regionRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Autowired
    AppUserRepositoryTest(
        AppUserRepository appUserRepository,
        RegionRepository regionRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository
    ) {
        this.appUserRepository = appUserRepository;
        this.regionRepository = regionRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    @Test
    void 사용자를_저장하고_식별자로_조회한다() {
        AppUser appUser = appUserRepository.saveAndFlush(
            new AppUser("visitor@example.com", "hashed-password", "홍길동", "010-1234-5678", AppUserStatus.ACTIVE)
        );

        AppUser foundUser = appUserRepository.findById(appUser.getUserId()).orElseThrow();

        assertThat(foundUser.getLoginIdentifier()).isEqualTo("visitor@example.com");
        assertThat(foundUser.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(foundUser.getName()).isEqualTo("홍길동");
        assertThat(foundUser.getPhone()).isEqualTo("010-1234-5678");
        assertThat(foundUser.getStatus()).isEqualTo(AppUserStatus.ACTIVE);
        assertThat(foundUser.getCreatedAt()).isNotNull();
        assertThat(foundUser.getUpdatedAt()).isNotNull();
    }

    @Test
    void 로그인_식별자는_중복될_수_없다() {
        appUserRepository.saveAndFlush(
            new AppUser("visitor@example.com", "hashed-password", "홍길동", "010-1234-5678", AppUserStatus.ACTIVE)
        );

        assertThatThrownBy(
            () -> appUserRepository.saveAndFlush(
                new AppUser("visitor@example.com", "other-hash", "김철수", "010-8765-4321", AppUserStatus.ACTIVE)
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 철회_중인_사용자_상태를_문자열로_매핑한다() {
        AppUser appUser = appUserRepository.saveAndFlush(
            new AppUser("withdrawn@example.com", "hashed-password", "홍길동", "010-1234-5678", AppUserStatus.WITHDRAWING)
        );

        assertThat(appUserRepository.findById(appUser.getUserId()).orElseThrow().getStatus())
            .isEqualTo(AppUserStatus.WITHDRAWING);
    }

    @Test
    void 전체관리자_사용자목록은_활성일반계정과_활성역할만_고정정렬로조회한다() {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        AppUser firstUser = saveUser("first@example.com", AppUserAccountKind.ORDINARY, AppUserStatus.ACTIVE);
        AppUser lastUser = saveUser("last@example.com", AppUserAccountKind.ORDINARY, AppUserStatus.ACTIVE);
        AppUser privilegedUser = saveUser(
            "admin@example.com",
            AppUserAccountKind.PRIVILEGED,
            AppUserStatus.ACTIVE
        );
        AppUser withdrawingUser = saveUser(
            "withdrawing@example.com",
            AppUserAccountKind.ORDINARY,
            AppUserStatus.WITHDRAWING
        );
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            firstUser,
            UserRole.REGION_ADMIN,
            region
        ));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            firstUser,
            UserRole.VISITOR,
            null
        ));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            privilegedUser,
            UserRole.VISITOR,
            null
        ));
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            withdrawingUser,
            UserRole.VISITOR,
            null
        ));

        List<PlatformAdminUserListProjection> users = appUserRepository.findPlatformAdminUserList();

        assertThat(users)
            .extracting(PlatformAdminUserListProjection::userId)
            .containsExactly(lastUser.getUserId(), firstUser.getUserId(), firstUser.getUserId());
        assertThat(users).extracting(PlatformAdminUserListProjection::role).containsExactly(
            null,
            UserRole.REGION_ADMIN,
            UserRole.VISITOR
        );
        assertThat(users.get(1))
            .extracting(
                PlatformAdminUserListProjection::regionId,
                PlatformAdminUserListProjection::regionName
            )
            .containsExactly(region.getRegionId(), "김해시");
    }

    private AppUser saveUser(
        String loginIdentifier,
        AppUserAccountKind accountKind,
        AppUserStatus status
    ) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "홍길동",
            "010-1234-5678",
            accountKind,
            status
        ));
    }

}
