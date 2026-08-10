package io.regionevent.regioneventbackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@DataJpaTest
@Import({
    ChangeRegionAdminRoleUseCase.class,
    PlatformAdminAuthorizationService.class,
    AppUserService.class,
    UserRoleAssignmentService.class,
    RegionService.class,
    ContentService.class,
    ChangeRegionAdminRoleAuditAtomicityTest.TestConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class ChangeRegionAdminRoleAuditAtomicityTest {

    private final ChangeRegionAdminRoleUseCase changeRegionAdminRoleUseCase;
    private final AppUserRepository appUserRepository;
    private final PlatformAdminAssignmentRepository platformAdminAssignmentRepository;
    private final RegionRepository regionRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final EntityManager entityManager;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @Autowired
    ChangeRegionAdminRoleAuditAtomicityTest(
        ChangeRegionAdminRoleUseCase changeRegionAdminRoleUseCase,
        AppUserRepository appUserRepository,
        PlatformAdminAssignmentRepository platformAdminAssignmentRepository,
        RegionRepository regionRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        EntityManager entityManager
    ) {
        this.changeRegionAdminRoleUseCase = changeRegionAdminRoleUseCase;
        this.appUserRepository = appUserRepository;
        this.platformAdminAssignmentRepository = platformAdminAssignmentRepository;
        this.regionRepository = regionRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 성공_감사_기록에_실패하면_지역관리자_임명도_롤백한다() {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> changeRegionAdminRoleUseCase.change(
            fixture.actor().getUserId(),
            fixture.targetUser().getUserId(),
            RegionAdminRoleChange.REGION_ADMIN,
            fixture.region().getRegionId(),
            "REGION_ADMIN_APPOINTMENT",
            "OPS-2026-0809-001",
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class);

        entityManager.clear();
        assertThat(userRoleAssignmentRepository.findByAppUserUserIdAndRoleAndStatusAndAppUserStatus(
            fixture.targetUser().getUserId(),
            UserRole.REGION_ADMIN,
            UserRoleAssignmentStatus.ACTIVE,
            AppUserStatus.ACTIVE
        )).isEmpty();
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        AppUser actor = appUserRepository.saveAndFlush(new AppUser(
            "platform-admin-" + suffix + "@example.com",
            "hashed-password",
            "전체관리자",
            "010-1234-5678",
            AppUserAccountKind.PRIVILEGED,
            AppUserStatus.ACTIVE
        ));
        platformAdminAssignmentRepository.saveAndFlush(new PlatformAdminAssignment(
            actor,
            PlatformAdminGrade.PLATFORM_ADMIN
        ));
        AppUser targetUser = appUserRepository.saveAndFlush(new AppUser(
            "target-user-" + suffix + "@example.com",
            "hashed-password",
            "대상 사용자",
            "010-9876-5432",
            AppUserStatus.ACTIVE
        ));
        Region region = regionRepository.saveAndFlush(new Region(
            "R" + suffix,
            "테스트 지역",
            true
        ));
        return new Fixture(actor, targetUser, region);
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    private record Fixture(AppUser actor, AppUser targetUser, Region region) {
    }
}
