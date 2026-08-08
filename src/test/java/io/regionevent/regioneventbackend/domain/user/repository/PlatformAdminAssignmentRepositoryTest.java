package io.regionevent.regioneventbackend.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class PlatformAdminAssignmentRepositoryTest {

    private final PlatformAdminAssignmentRepository platformAdminAssignmentRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;

    @Autowired
    PlatformAdminAssignmentRepositoryTest(
        PlatformAdminAssignmentRepository platformAdminAssignmentRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager
    ) {
        this.platformAdminAssignmentRepository = platformAdminAssignmentRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 활성_특권_계정의_고권한_배정을_조회한다() {
        AppUser appUser = savePrivilegedUser(AppUserStatus.ACTIVE);
        PlatformAdminAssignment assignment = platformAdminAssignmentRepository.saveAndFlush(
            new PlatformAdminAssignment(appUser, PlatformAdminGrade.PLATFORM_ADMIN)
        );
        entityManager.clear();

        PlatformAdminAssignment foundAssignment = findActiveAssignment(appUser.getUserId()).orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(foundAssignment.getPlatformAdminAssignmentId())
            .isEqualTo(assignment.getPlatformAdminAssignmentId());
        assertThat(foundAssignment.getGrade()).isEqualTo(PlatformAdminGrade.PLATFORM_ADMIN);
        assertThat(foundAssignment.getStatus()).isEqualTo(PlatformAdminAssignmentStatus.ACTIVE);
        assertThat(persistenceUnitUtil.isLoaded(foundAssignment, "appUser")).isTrue();
        assertThat(foundAssignment.getAppUser().getUserId()).isEqualTo(appUser.getUserId());
    }

    @Test
    void 비활성_배정은_활성_고권한_배정으로_조회되지_않는다() {
        AppUser appUser = savePrivilegedUser(AppUserStatus.ACTIVE);
        PlatformAdminAssignment assignment = new PlatformAdminAssignment(
            appUser,
            PlatformAdminGrade.SUPER_ADMIN
        );
        assignment.inactivate(Instant.parse("2026-08-08T00:00:00Z"), "ADMIN_ACCOUNT_INACTIVATION");
        platformAdminAssignmentRepository.saveAndFlush(assignment);

        assertThat(findActiveAssignment(appUser.getUserId())).isEmpty();
    }

    @Test
    void 비활성_계정의_배정은_활성_고권한_배정으로_조회되지_않는다() {
        AppUser appUser = savePrivilegedUser(AppUserStatus.WITHDRAWING);
        platformAdminAssignmentRepository.saveAndFlush(
            new PlatformAdminAssignment(appUser, PlatformAdminGrade.SUPER_ADMIN)
        );

        assertThat(findActiveAssignment(appUser.getUserId())).isEmpty();
    }

    @Test
    void 사용자당_활성_고권한_배정은_하나만_저장한다() {
        AppUser appUser = savePrivilegedUser(AppUserStatus.ACTIVE);
        platformAdminAssignmentRepository.saveAndFlush(
            new PlatformAdminAssignment(appUser, PlatformAdminGrade.SUPER_ADMIN)
        );

        assertThatThrownBy(() -> platformAdminAssignmentRepository.saveAndFlush(
            new PlatformAdminAssignment(appUser, PlatformAdminGrade.PLATFORM_ADMIN)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 일반_계정에는_고권한_배정을_만들지_않는다() {
        AppUser ordinaryUser = new AppUser(
            "ordinary@example.com",
            "hashed-password",
            "일반 사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        );

        assertThatThrownBy(() -> new PlatformAdminAssignment(
            ordinaryUser,
            PlatformAdminGrade.PLATFORM_ADMIN
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private Optional<PlatformAdminAssignment> findActiveAssignment(Long userId) {
        return platformAdminAssignmentRepository
            .findByAppUserUserIdAndStatusAndAppUserStatusAndAppUserAccountKind(
                userId,
                PlatformAdminAssignmentStatus.ACTIVE,
                AppUserStatus.ACTIVE,
                AppUserAccountKind.PRIVILEGED
            );
    }

    private AppUser savePrivilegedUser(AppUserStatus status) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        return appUserRepository.saveAndFlush(new AppUser(
            "admin-" + suffix + "@example.com",
            "hashed-password",
            "전체 관리자",
            "010-1234-5678",
            AppUserAccountKind.PRIVILEGED,
            status
        ));
    }
}
