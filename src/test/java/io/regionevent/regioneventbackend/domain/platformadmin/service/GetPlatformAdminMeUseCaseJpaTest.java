package io.regionevent.regioneventbackend.domain.platformadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@DataJpaTest
@Import({GetPlatformAdminMeUseCase.class, PlatformAdminAuthorizationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class GetPlatformAdminMeUseCaseJpaTest {

    private final GetPlatformAdminMeUseCase getPlatformAdminMeUseCase;
    private final AppUserRepository appUserRepository;
    private final PlatformAdminAssignmentRepository platformAdminAssignmentRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    GetPlatformAdminMeUseCaseJpaTest(
        GetPlatformAdminMeUseCase getPlatformAdminMeUseCase,
        AppUserRepository appUserRepository,
        PlatformAdminAssignmentRepository platformAdminAssignmentRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.getPlatformAdminMeUseCase = getPlatformAdminMeUseCase;
        this.appUserRepository = appUserRepository;
        this.platformAdminAssignmentRepository = platformAdminAssignmentRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void get_비활성배정의활성PRIVILEGED계정_본인식별자를반환하고아무것도변경하지않는다() {
        Long actorUserId = createPrivilegedUser(
            AppUserStatus.ACTIVE,
            PlatformAdminGrade.PLATFORM_ADMIN,
            true
        );
        DatabaseSnapshot before = databaseSnapshot();

        Long result = getPlatformAdminMeUseCase.get(actorUserId);

        assertThat(result).isEqualTo(actorUserId);
        assertThat(databaseSnapshot()).isEqualTo(before);
    }

    @Test
    void get_비활성PRIVILEGED계정_권한오류를반환하고아무것도변경하지않는다() {
        Long actorUserId = createPrivilegedUser(
            AppUserStatus.WITHDRAWING,
            PlatformAdminGrade.SUPER_ADMIN,
            false
        );
        DatabaseSnapshot before = databaseSnapshot();

        assertForbidden(() -> getPlatformAdminMeUseCase.get(actorUserId));

        assertThat(databaseSnapshot()).isEqualTo(before);
    }

    @Test
    void get_활성ORDINARY계정_권한오류를반환하고아무것도변경하지않는다() {
        Long actorUserId = createOrdinaryUser();
        DatabaseSnapshot before = databaseSnapshot();

        assertForbidden(() -> getPlatformAdminMeUseCase.get(actorUserId));

        assertThat(databaseSnapshot()).isEqualTo(before);
    }

    private Long createPrivilegedUser(
        AppUserStatus status,
        PlatformAdminGrade grade,
        boolean inactivateAssignment
    ) {
        return transactionTemplate.execute(transactionStatus -> {
            AppUser user = saveUser(AppUserAccountKind.PRIVILEGED, status);
            PlatformAdminAssignment assignment = new PlatformAdminAssignment(user, grade);
            if (inactivateAssignment) {
                assignment.inactivate(
                    Instant.parse("2026-08-21T00:00:00Z"),
                    "ADMIN_ACCOUNT_INACTIVATION"
                );
            }
            platformAdminAssignmentRepository.saveAndFlush(assignment);
            return user.getUserId();
        });
    }

    private Long createOrdinaryUser() {
        return transactionTemplate.execute(transactionStatus ->
            saveUser(AppUserAccountKind.ORDINARY, AppUserStatus.ACTIVE).getUserId()
        );
    }

    private AppUser saveUser(AppUserAccountKind accountKind, AppUserStatus status) {
        return appUserRepository.saveAndFlush(new AppUser(
            UUID.randomUUID() + "@example.com",
            "password-hash",
            "조회 요청자",
            "01012345678",
            accountKind,
            status
        ));
    }

    private DatabaseSnapshot databaseSnapshot() {
        return transactionTemplate.execute(transactionStatus -> new DatabaseSnapshot(
            appUserRepository.findAll().stream()
                .map(AppUserSnapshot::from)
                .sorted(Comparator.comparing(AppUserSnapshot::userId))
                .toList(),
            platformAdminAssignmentRepository.findAll().stream()
                .map(AssignmentSnapshot::from)
                .sorted(Comparator.comparing(AssignmentSnapshot::assignmentId))
                .toList(),
            auditEventRepository.count(),
            auditEventActorLinkRepository.count()
        ));
    }

    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
    }

    private record DatabaseSnapshot(
        List<AppUserSnapshot> users,
        List<AssignmentSnapshot> assignments,
        long auditEventCount,
        long auditActorLinkCount
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
                assignment.getAppUser().getUserId(),
                assignment.getGrade(),
                assignment.getStatus(),
                assignment.getGrantedAt(),
                assignment.getInactivatedAt(),
                assignment.getInactiveReasonCode()
            );
        }
    }
}
