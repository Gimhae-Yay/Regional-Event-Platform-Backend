package io.regionevent.regioneventbackend.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.support.mysql.NonTransactionalMySqlTestSupport;
import io.regionevent.regioneventbackend.support.mysql.SharedMySqlTestContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserRoleAssignmentRepositoryMySqlTest extends NonTransactionalMySqlTestSupport {

    private final AppUserRepository appUserRepository;
    private final RegionRepository regionRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    UserRoleAssignmentRepositoryMySqlTest(
        AppUserRepository appUserRepository,
        RegionRepository regionRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.appUserRepository = appUserRepository;
        this.regionRepository = regionRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.registerDataSourceProperties(registry);
    }

    @Test
    @Timeout(10)
    void MySQL에서_회수된_배정은_활성_배정_유일성에_포함하지_않는다() {
        Fixture fixture = transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region previousRegion = regionRepository.save(new Region("A" + suffix, "이전 지역", true));
            Region nextRegion = regionRepository.save(new Region("B" + suffix, "새 지역", true));
            AppUser appUser = appUserRepository.save(new AppUser(
                "admin-" + suffix + "@example.com",
                "hashed-password",
                "지역관리자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            UserRoleAssignment revokedAssignment = userRoleAssignmentRepository.saveAndFlush(
                new UserRoleAssignment(appUser, UserRole.REGION_ADMIN, previousRegion)
            );
            revokedAssignment.revoke(Instant.parse("2026-08-08T00:00:00Z"), "REGION_ADMIN_REASSIGNMENT");
            userRoleAssignmentRepository.saveAndFlush(revokedAssignment);
            UserRoleAssignment activeAssignment = userRoleAssignmentRepository.saveAndFlush(
                new UserRoleAssignment(appUser, UserRole.REGION_ADMIN, nextRegion)
            );
            return new Fixture(appUser, nextRegion, activeAssignment);
        });

        assertThat(fixture).isNotNull();
        assertThat(userRoleAssignmentRepository.findActiveRegionAdminsForUpdate(
            fixture.region().getRegionId()
        )).hasSize(1);
        assertThat(userRoleAssignmentRepository.findById(fixture.assignment().getRoleAssignmentId()))
            .hasValueSatisfying(assignment -> assertThat(assignment.getRole()).isEqualTo(UserRole.REGION_ADMIN));
    }

    @Test
    @Timeout(10)
    void MySQL에서_동일_사용자의_활성_지역관리자_배정은_중복될_수_없다() {
        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            Region firstRegion = regionRepository.save(new Region("A" + suffix, "첫 지역", true));
            Region secondRegion = regionRepository.save(new Region("B" + suffix, "둘째 지역", true));
            AppUser appUser = appUserRepository.save(new AppUser(
                "duplicate-" + suffix + "@example.com",
                "hashed-password",
                "지역관리자",
                "010-1234-5678",
                AppUserStatus.ACTIVE
            ));
            userRoleAssignmentRepository.saveAndFlush(
                new UserRoleAssignment(appUser, UserRole.REGION_ADMIN, firstRegion)
            );
            return userRoleAssignmentRepository.saveAndFlush(
                new UserRoleAssignment(appUser, UserRole.REGION_ADMIN, secondRegion)
            );
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    private record Fixture(
        AppUser user,
        Region region,
        UserRoleAssignment assignment
    ) {
    }
}
