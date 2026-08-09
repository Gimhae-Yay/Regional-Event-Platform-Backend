package io.regionevent.regioneventbackend.domain.platformadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAssignmentService;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@DataJpaTest
@Import({
    CreateAdminAccountUseCase.class,
    AppUserService.class,
    PlatformAdminAssignmentService.class,
    PlatformAdminAuthorizationService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class CreateAdminAccountUseCaseJpaTest {

    private final CreateAdminAccountUseCase createAdminAccountUseCase;
    private final AppUserRepository appUserRepository;
    private final PlatformAdminAssignmentRepository platformAdminAssignmentRepository;
    private final TransactionTemplate transactionTemplate;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    CreateAdminAccountUseCaseJpaTest(
        CreateAdminAccountUseCase createAdminAccountUseCase,
        AppUserRepository appUserRepository,
        PlatformAdminAssignmentRepository platformAdminAssignmentRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.createAdminAccountUseCase = createAdminAccountUseCase;
        this.appUserRepository = appUserRepository;
        this.platformAdminAssignmentRepository = platformAdminAssignmentRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void create_감사기록저장에실패하면_계정과배정을모두롤백한다() {
        Long superAdminUserId = createSuperAdmin();
        long userCount = appUserRepository.count();
        long assignmentCount = platformAdminAssignmentRepository.count();
        when(passwordEncoder.encode(anyString())).thenReturn("password-hash");
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> create(superAdminUserId, "new-admin@example.com"))
            .isInstanceOf(IllegalStateException.class);

        assertThat(appUserRepository.count()).isEqualTo(userCount);
        assertThat(platformAdminAssignmentRepository.count()).isEqualTo(assignmentCount);
    }

    @Test
    void create_기존로그인식별자면_중복오류를반환하고계정과배정을만들지않는다() {
        Long superAdminUserId = createSuperAdmin();
        transactionTemplate.executeWithoutResult(status -> appUserRepository.save(new AppUser(
            "duplicate@example.com",
            "password-hash",
            "기존 사용자",
            "01012345678",
            AppUserStatus.ACTIVE
        )));
        long userCount = appUserRepository.count();
        long assignmentCount = platformAdminAssignmentRepository.count();

        assertThatThrownBy(() -> create(superAdminUserId, "duplicate@example.com"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_LOGIN_IDENTIFIER)
            );

        assertThat(appUserRepository.count()).isEqualTo(userCount);
        assertThat(platformAdminAssignmentRepository.count()).isEqualTo(assignmentCount);
    }

    private Long createSuperAdmin() {
        return transactionTemplate.execute(status -> {
            AppUser user = appUserRepository.save(new AppUser(
                "super-admin-" + UUID.randomUUID() + "@example.com",
                "password-hash",
                "슈퍼관리자",
                "01012345678",
                AppUserAccountKind.PRIVILEGED,
                AppUserStatus.ACTIVE
            ));
            platformAdminAssignmentRepository.save(new PlatformAdminAssignment(user, PlatformAdminGrade.SUPER_ADMIN));
            return user.getUserId();
        });
    }

    private CreateAdminAccountResult create(Long superAdminUserId, String email) {
        return createAdminAccountUseCase.create(
            superAdminUserId,
            new CreateAdminAccountUseCase.CreateAdminAccountCommand(
                email,
                "LocalStamp!2026",
                "새 관리자",
                "01012345678",
                "PLATFORM_ADMIN",
                "account-creation/v1",
                "OPS-2026-0809-001"
            ),
            UUID.randomUUID()
        );
    }
}
