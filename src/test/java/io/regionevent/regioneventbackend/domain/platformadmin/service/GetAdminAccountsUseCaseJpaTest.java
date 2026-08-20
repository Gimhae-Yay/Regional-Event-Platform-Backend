package io.regionevent.regioneventbackend.domain.platformadmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAssignmentService;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@DataJpaTest
@Import({
    GetAdminAccountsUseCase.class,
    PlatformAdminAssignmentService.class,
    PlatformAdminAuthorizationService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class GetAdminAccountsUseCaseJpaTest {

    private final GetAdminAccountsUseCase getAdminAccountsUseCase;
    private final AppUserRepository appUserRepository;
    private final PlatformAdminAssignmentRepository platformAdminAssignmentRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    GetAdminAccountsUseCaseJpaTest(
        GetAdminAccountsUseCase getAdminAccountsUseCase,
        AppUserRepository appUserRepository,
        PlatformAdminAssignmentRepository platformAdminAssignmentRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        EntityManager entityManager,
        PlatformTransactionManager transactionManager
    ) {
        this.getAdminAccountsUseCase = getAdminAccountsUseCase;
        this.appUserRepository = appUserRepository;
        this.platformAdminAssignmentRepository = platformAdminAssignmentRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void get_활성PRIVILEGED계정요청_연결된두등급두상태를정렬하고아무것도변경하지않는다() {
        Fixture fixture = createFixture();
        List<AppUserSnapshot> usersBefore = appUserSnapshots();
        List<AssignmentSnapshot> assignmentsBefore = assignmentSnapshots();
        long auditEventCountBefore = auditEventRepository.count();
        long auditActorLinkCountBefore = auditEventActorLinkRepository.count();

        List<AdminAccountListInfo> result = getAdminAccountsUseCase.get(fixture.actorUserId());

        assertThat(result).containsExactly(
            new AdminAccountListInfo(
                fixture.actorUserId(),
                fixture.actorLoginIdentifier(),
                "조회 요청자",
                PlatformAdminGrade.SUPER_ADMIN,
                PlatformAdminAssignmentStatus.INACTIVE,
                Instant.parse("2026-08-20T04:00:00Z"),
                Instant.parse("2026-08-20T05:00:00Z")
            ),
            new AdminAccountListInfo(
                fixture.tiedHigherUserId(),
                fixture.tiedHigherLoginIdentifier(),
                "비활성 플랫폼 관리자",
                PlatformAdminGrade.PLATFORM_ADMIN,
                PlatformAdminAssignmentStatus.INACTIVE,
                Instant.parse("2026-08-20T02:00:00Z"),
                Instant.parse("2026-08-20T03:00:00Z")
            ),
            new AdminAccountListInfo(
                fixture.tiedLowerUserId(),
                fixture.tiedLowerLoginIdentifier(),
                "활성 플랫폼 관리자",
                PlatformAdminGrade.PLATFORM_ADMIN,
                PlatformAdminAssignmentStatus.ACTIVE,
                Instant.parse("2026-08-20T02:00:00Z"),
                null
            ),
            new AdminAccountListInfo(
                fixture.oldestUserId(),
                fixture.oldestLoginIdentifier(),
                "활성 슈퍼 관리자",
                PlatformAdminGrade.SUPER_ADMIN,
                PlatformAdminAssignmentStatus.ACTIVE,
                Instant.parse("2026-08-20T01:00:00Z"),
                null
            )
        );
        assertThat(appUserSnapshots()).isEqualTo(usersBefore);
        assertThat(assignmentSnapshots()).isEqualTo(assignmentsBefore);
        assertThat(auditEventRepository.count()).isEqualTo(auditEventCountBefore);
        assertThat(auditEventActorLinkRepository.count()).isEqualTo(auditActorLinkCountBefore);
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            AppUser actor = savePrivilegedUser("조회 요청자", AppUserStatus.ACTIVE);
            PlatformAdminAssignment actorAssignment = saveAssignment(
                actor,
                PlatformAdminGrade.SUPER_ADMIN,
                Instant.parse("2026-08-20T05:00:00Z")
            );
            AppUser oldest = savePrivilegedUser("활성 슈퍼 관리자", AppUserStatus.ACTIVE);
            PlatformAdminAssignment oldestAssignment = saveAssignment(
                oldest,
                PlatformAdminGrade.SUPER_ADMIN,
                null
            );
            AppUser tiedLower = savePrivilegedUser("활성 플랫폼 관리자", AppUserStatus.ACTIVE);
            PlatformAdminAssignment tiedLowerAssignment = saveAssignment(
                tiedLower,
                PlatformAdminGrade.PLATFORM_ADMIN,
                null
            );
            AppUser tiedHigher = savePrivilegedUser("비활성 플랫폼 관리자", AppUserStatus.WITHDRAWING);
            PlatformAdminAssignment tiedHigherAssignment = saveAssignment(
                tiedHigher,
                PlatformAdminGrade.PLATFORM_ADMIN,
                Instant.parse("2026-08-20T03:00:00Z")
            );
            AppUser unlinked = savePrivilegedUser("연결 해제 관리자", AppUserStatus.ACTIVE);
            PlatformAdminAssignment unlinkedAssignment = saveAssignment(
                unlinked,
                PlatformAdminGrade.PLATFORM_ADMIN,
                null
            );
            setGrantedAt(actorAssignment, Instant.parse("2026-08-20T04:00:00Z"));
            setGrantedAt(oldestAssignment, Instant.parse("2026-08-20T01:00:00Z"));
            setGrantedAt(tiedLowerAssignment, Instant.parse("2026-08-20T02:00:00Z"));
            setGrantedAt(tiedHigherAssignment, Instant.parse("2026-08-20T02:00:00Z"));
            unlinkAssignment(unlinkedAssignment);
            entityManager.clear();
            return new Fixture(
                actor.getUserId(),
                actor.getLoginIdentifier(),
                oldest.getUserId(),
                oldest.getLoginIdentifier(),
                tiedLower.getUserId(),
                tiedLower.getLoginIdentifier(),
                tiedHigher.getUserId(),
                tiedHigher.getLoginIdentifier()
            );
        });
    }

    private AppUser savePrivilegedUser(String name, AppUserStatus status) {
        return appUserRepository.saveAndFlush(new AppUser(
            UUID.randomUUID() + "@example.com",
            "password-hash",
            name,
            "01012345678",
            AppUserAccountKind.PRIVILEGED,
            status
        ));
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

    private List<AppUserSnapshot> appUserSnapshots() {
        return transactionTemplate.execute(status -> appUserRepository.findAll().stream()
            .map(AppUserSnapshot::from)
            .sorted(Comparator.comparing(AppUserSnapshot::userId))
            .toList());
    }

    private List<AssignmentSnapshot> assignmentSnapshots() {
        return transactionTemplate.execute(status -> platformAdminAssignmentRepository.findAll().stream()
            .map(AssignmentSnapshot::from)
            .sorted(Comparator.comparing(AssignmentSnapshot::assignmentId))
            .toList());
    }

    private record Fixture(
        Long actorUserId,
        String actorLoginIdentifier,
        Long oldestUserId,
        String oldestLoginIdentifier,
        Long tiedLowerUserId,
        String tiedLowerLoginIdentifier,
        Long tiedHigherUserId,
        String tiedHigherLoginIdentifier
    ) {
    }

    private record AppUserSnapshot(
        Long userId,
        String loginIdentifier,
        String passwordHash,
        String name,
        String phone,
        AppUserAccountKind accountKind,
        AppUserStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {

        private static AppUserSnapshot from(AppUser user) {
            return new AppUserSnapshot(
                user.getUserId(),
                user.getLoginIdentifier(),
                user.getPasswordHash(),
                user.getName(),
                user.getPhone(),
                user.getAccountKind(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
            );
        }
    }

    private record AssignmentSnapshot(
        Long assignmentId,
        Long userId,
        PlatformAdminGrade grade,
        PlatformAdminAssignmentStatus status,
        Instant grantedAt,
        Instant inactivatedAt,
        String inactiveReasonCode
    ) {

        private static AssignmentSnapshot from(PlatformAdminAssignment assignment) {
            return new AssignmentSnapshot(
                assignment.getPlatformAdminAssignmentId(),
                assignment.getAppUser() == null ? null : assignment.getAppUser().getUserId(),
                assignment.getGrade(),
                assignment.getStatus(),
                assignment.getGrantedAt(),
                assignment.getInactivatedAt(),
                assignment.getInactiveReasonCode()
            );
        }
    }
}
