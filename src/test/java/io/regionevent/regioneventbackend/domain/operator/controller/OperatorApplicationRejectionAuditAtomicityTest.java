package io.regionevent.regioneventbackend.domain.operator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;
import io.regionevent.regioneventbackend.domain.operator.repository.OperatorApplicationRepository;
import io.regionevent.regioneventbackend.domain.operator.service.RejectOperatorApplicationUseCase;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;
import io.regionevent.regioneventbackend.support.jpa.AtomicityJpaTestConfiguration;

@DataJpaTest
@Import(AtomicityJpaTestConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class OperatorApplicationRejectionAuditAtomicityTest {

    private final RejectOperatorApplicationUseCase rejectOperatorApplicationUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final OperatorApplicationRepository operatorApplicationRepository;
    private final EntityManager entityManager;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    OperatorApplicationRejectionAuditAtomicityTest(
        RejectOperatorApplicationUseCase rejectOperatorApplicationUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        OperatorApplicationRepository operatorApplicationRepository,
        EntityManager entityManager
    ) {
        this.rejectOperatorApplicationUseCase = rejectOperatorApplicationUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.operatorApplicationRepository = operatorApplicationRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 감사_기록에_실패하면_반려를_함께_롤백한다() {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> rejectOperatorApplicationUseCase.reject(
            fixture.admin().getUserId(),
            fixture.application().getOperatorApplicationId(),
            "사유",
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class);

        entityManager.clear();
        assertThat(operatorApplicationRepository.findById(fixture.application().getOperatorApplicationId()))
            .hasValueSatisfying(application -> {
                assertThat(application.getStatus()).isEqualTo(OperatorApplicationStatus.PENDING);
                assertThat(application.getInspectedUser()).isNull();
                assertThat(application.getRejectedReason()).isNull();
            });
        assertThat(userRoleAssignmentRepository.findByAppUserUserIdAndRoleAndStatusAndAppUserStatus(
            fixture.applicant().getUserId(),
            UserRole.OPERATOR,
            UserRoleAssignmentStatus.ACTIVE,
            AppUserStatus.ACTIVE
        )).isEmpty();
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "테스트 지역", true));
        AppUser admin = saveUser("admin-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(admin, UserRole.REGION_ADMIN, region));
        AppUser applicant = saveUser("applicant-" + suffix);
        OperatorApplication application = operatorApplicationRepository.saveAndFlush(new OperatorApplication(
            applicant,
            region,
            "사업자 정보",
            OperatorApplicationStatus.PENDING,
            null,
            null
        ));
        return new Fixture(admin, applicant, application);
    }

    private AppUser saveUser(String prefix) {
        return appUserRepository.saveAndFlush(new AppUser(
            prefix + Long.toUnsignedString(System.nanoTime()) + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private record Fixture(
        AppUser admin,
        AppUser applicant,
        OperatorApplication application
    ) {
    }
}
