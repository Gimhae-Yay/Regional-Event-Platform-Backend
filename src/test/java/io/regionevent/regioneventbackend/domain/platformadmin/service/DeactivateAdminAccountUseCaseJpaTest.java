package io.regionevent.regioneventbackend.domain.platformadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAssignmentService;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@DataJpaTest
@Import({
    DeactivateAdminAccountUseCase.class,
    AppUserService.class,
    PlatformAdminAssignmentService.class,
    PlatformAdminAuthorizationService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class DeactivateAdminAccountUseCaseJpaTest {

    private static final Instant NOW = Instant.parse("2026-08-10T01:00:00Z");

    private final DeactivateAdminAccountUseCase deactivateAdminAccountUseCase;
    private final AppUserRepository appUserRepository;
    private final PlatformAdminAssignmentRepository platformAdminAssignmentRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final TransactionTemplate transactionTemplate;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @Autowired
    DeactivateAdminAccountUseCaseJpaTest(
        DeactivateAdminAccountUseCase deactivateAdminAccountUseCase,
        AppUserRepository appUserRepository,
        PlatformAdminAssignmentRepository platformAdminAssignmentRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.deactivateAdminAccountUseCase = deactivateAdminAccountUseCase;
        this.appUserRepository = appUserRepository;
        this.platformAdminAssignmentRepository = platformAdminAssignmentRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void deactivate_감사기록저장에실패하면_계정비활성화와감사기록을모두롤백한다() {
        Fixture fixture = createFixture();
        when(clock.instant()).thenReturn(NOW);
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> deactivate(fixture.actorUserId(), fixture.targetUserId()))
            .isInstanceOf(IllegalStateException.class);

        assertThat(platformAdminAssignmentRepository.findById(fixture.targetAssignmentId()))
            .hasValueSatisfying(target -> {
                assertThat(target.getStatus()).isEqualTo(PlatformAdminAssignmentStatus.ACTIVE);
                assertThat(target.getInactivatedAt()).isNull();
                assertThat(target.getInactiveReasonCode()).isNull();
            });
        assertThat(auditEventRepository.count()).isZero();
        assertThat(auditEventActorLinkRepository.count()).isZero();
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            AppUser actor = saveSuperAdmin("actor-" + UUID.randomUUID() + "@example.com");
            AppUser target = savePlatformAdmin("target-" + UUID.randomUUID() + "@example.com");
            PlatformAdminAssignment targetAssignment = platformAdminAssignmentRepository
                .findByAppUserUserId(target.getUserId())
                .orElseThrow();
            return new Fixture(actor.getUserId(), target.getUserId(), targetAssignment.getPlatformAdminAssignmentId());
        });
    }

    private AppUser saveSuperAdmin(String email) {
        return savePrivilegedUser(email, PlatformAdminGrade.SUPER_ADMIN);
    }

    private AppUser savePlatformAdmin(String email) {
        return savePrivilegedUser(email, PlatformAdminGrade.PLATFORM_ADMIN);
    }

    private AppUser savePrivilegedUser(String email, PlatformAdminGrade grade) {
        AppUser user = appUserRepository.save(new AppUser(
            email,
            "password-hash",
            "관리자",
            "01012345678",
            AppUserAccountKind.PRIVILEGED,
            AppUserStatus.ACTIVE
        ));
        platformAdminAssignmentRepository.save(new PlatformAdminAssignment(user, grade));
        return user;
    }

    private DeactivateAdminAccountResult deactivate(Long actorUserId, Long targetUserId) {
        return deactivateAdminAccountUseCase.deactivate(
            actorUserId,
            targetUserId,
            new DeactivateAdminAccountUseCase.DeactivateAdminAccountCommand(
                "ADMIN_ACCOUNT_INACTIVATION",
                "OPS-2026-0810-001"
            ),
            UUID.randomUUID()
        );
    }

    private record Fixture(Long actorUserId, Long targetUserId, Long targetAssignmentId) {
    }
}
