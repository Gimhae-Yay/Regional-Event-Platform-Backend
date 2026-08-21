package io.regionevent.regioneventbackend.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;
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
    void 비활성화된_고권한_계정은_새_배정을_만들지_않는다() {
        AppUser appUser = savePrivilegedUser(AppUserStatus.ACTIVE);
        PlatformAdminAssignment assignment = platformAdminAssignmentRepository.saveAndFlush(
            new PlatformAdminAssignment(appUser, PlatformAdminGrade.SUPER_ADMIN)
        );
        assignment.inactivate(Instant.parse("2026-08-08T00:00:00Z"), "ADMIN_ACCOUNT_INACTIVATION");
        platformAdminAssignmentRepository.saveAndFlush(assignment);

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

    @Test
    void 전체관리자계정목록은_연결된PRIVILEGED배정을_등급상태전체와고정순서로조회한다() {
        Instant newestGrantedAt = Instant.parse("2026-08-20T04:00:00Z");
        Instant tiedGrantedAt = Instant.parse("2026-08-20T03:00:00Z");
        Instant oldestGrantedAt = Instant.parse("2026-08-20T02:00:00Z");
        AppUser oldestUser = savePrivilegedUser(AppUserStatus.ACTIVE);
        PlatformAdminAssignment oldestAssignment = saveAssignment(
            oldestUser,
            PlatformAdminGrade.SUPER_ADMIN,
            Instant.parse("2026-08-20T05:00:00Z")
        );
        AppUser tiedLowerUser = savePrivilegedUser(AppUserStatus.ACTIVE);
        PlatformAdminAssignment tiedLowerAssignment = saveAssignment(
            tiedLowerUser,
            PlatformAdminGrade.PLATFORM_ADMIN,
            null
        );
        AppUser tiedHigherUser = savePrivilegedUser(AppUserStatus.WITHDRAWING);
        PlatformAdminAssignment tiedHigherAssignment = saveAssignment(
            tiedHigherUser,
            PlatformAdminGrade.SUPER_ADMIN,
            Instant.parse("2026-08-20T06:00:00Z")
        );
        AppUser newestUser = savePrivilegedUser(AppUserStatus.ACTIVE);
        PlatformAdminAssignment newestAssignment = saveAssignment(
            newestUser,
            PlatformAdminGrade.PLATFORM_ADMIN,
            null
        );
        AppUser unlinkedUser = savePrivilegedUser(AppUserStatus.ACTIVE);
        PlatformAdminAssignment unlinkedAssignment = saveAssignment(
            unlinkedUser,
            PlatformAdminGrade.PLATFORM_ADMIN,
            null
        );
        setGrantedAt(oldestAssignment, oldestGrantedAt);
        setGrantedAt(tiedLowerAssignment, tiedGrantedAt);
        setGrantedAt(tiedHigherAssignment, tiedGrantedAt);
        setGrantedAt(newestAssignment, newestGrantedAt);
        unlinkAssignment(unlinkedAssignment);
        entityManager.clear();

        List<PlatformAdminAccountListProjection> adminAccounts =
            platformAdminAssignmentRepository.findPlatformAdminAccountList();

        assertThat(adminAccounts)
            .extracting(
                PlatformAdminAccountListProjection::userId,
                PlatformAdminAccountListProjection::loginIdentifier,
                PlatformAdminAccountListProjection::name,
                PlatformAdminAccountListProjection::grade,
                PlatformAdminAccountListProjection::status,
                PlatformAdminAccountListProjection::grantedAt,
                PlatformAdminAccountListProjection::inactivatedAt
            )
            .containsExactly(
                tuple(
                    newestUser.getUserId(),
                    newestUser.getLoginIdentifier(),
                    newestUser.getName(),
                    PlatformAdminGrade.PLATFORM_ADMIN,
                    PlatformAdminAssignmentStatus.ACTIVE,
                    newestGrantedAt,
                    null
                ),
                tuple(
                    tiedHigherUser.getUserId(),
                    tiedHigherUser.getLoginIdentifier(),
                    tiedHigherUser.getName(),
                    PlatformAdminGrade.SUPER_ADMIN,
                    PlatformAdminAssignmentStatus.INACTIVE,
                    tiedGrantedAt,
                    Instant.parse("2026-08-20T06:00:00Z")
                ),
                tuple(
                    tiedLowerUser.getUserId(),
                    tiedLowerUser.getLoginIdentifier(),
                    tiedLowerUser.getName(),
                    PlatformAdminGrade.PLATFORM_ADMIN,
                    PlatformAdminAssignmentStatus.ACTIVE,
                    tiedGrantedAt,
                    null
                ),
                tuple(
                    oldestUser.getUserId(),
                    oldestUser.getLoginIdentifier(),
                    oldestUser.getName(),
                    PlatformAdminGrade.SUPER_ADMIN,
                    PlatformAdminAssignmentStatus.INACTIVE,
                    oldestGrantedAt,
                    Instant.parse("2026-08-20T05:00:00Z")
                )
            );
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

    private PlatformAdminAssignment saveAssignment(
        AppUser appUser,
        PlatformAdminGrade grade,
        Instant inactivatedAt
    ) {
        PlatformAdminAssignment assignment = new PlatformAdminAssignment(appUser, grade);
        if (inactivatedAt != null) {
            assignment.inactivate(inactivatedAt, "ADMIN_ACCOUNT_INACTIVATION");
        }
        return platformAdminAssignmentRepository.saveAndFlush(assignment);
    }

    private void setGrantedAt(PlatformAdminAssignment assignment, Instant grantedAt) {
        entityManager.createQuery("""
                UPDATE PlatformAdminAssignment target
                SET target.grantedAt = :grantedAt
                WHERE target.platformAdminAssignmentId = :assignmentId
                """)
            .setParameter("grantedAt", grantedAt)
            .setParameter("assignmentId", assignment.getPlatformAdminAssignmentId())
            .executeUpdate();
    }

    private void unlinkAssignment(PlatformAdminAssignment assignment) {
        entityManager.createQuery("""
                UPDATE PlatformAdminAssignment target
                SET target.appUser = NULL
                WHERE target.platformAdminAssignmentId = :assignmentId
                """)
            .setParameter("assignmentId", assignment.getPlatformAdminAssignmentId())
            .executeUpdate();
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
