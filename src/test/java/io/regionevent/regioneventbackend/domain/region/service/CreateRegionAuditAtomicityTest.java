package io.regionevent.regioneventbackend.domain.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.region.service.CreateRegionUseCase.CreateRegionCommand;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminAssignmentRepository;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.support.jpa.CleanH2Database;

@DataJpaTest
@Import({
    CreateRegionUseCase.class,
    RegionService.class,
    PlatformAdminAuthorizationService.class,
    CreateRegionAuditAtomicityTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@CleanH2Database
class CreateRegionAuditAtomicityTest {

    private final CreateRegionUseCase createRegionUseCase;
    private final AppUserRepository appUserRepository;
    private final PlatformAdminAssignmentRepository platformAdminAssignmentRepository;
    private final RegionRepository regionRepository;
    private final AuditEventRepository auditEventRepository;
    private final TransactionTemplate transactionTemplate;

    @MockitoBean
    private RecordAuditEventUseCase recordAuditEventUseCase;

    @Autowired
    CreateRegionAuditAtomicityTest(
        CreateRegionUseCase createRegionUseCase,
        AppUserRepository appUserRepository,
        PlatformAdminAssignmentRepository platformAdminAssignmentRepository,
        RegionRepository regionRepository,
        AuditEventRepository auditEventRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.createRegionUseCase = createRegionUseCase;
        this.appUserRepository = appUserRepository;
        this.platformAdminAssignmentRepository = platformAdminAssignmentRepository;
        this.regionRepository = regionRepository;
        this.auditEventRepository = auditEventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void create_성공감사기록이실패하면_지역생성을롤백한다() {
        AppUser actor = createPlatformAdmin();
        doThrow(new IllegalStateException("audit storage failure"))
            .when(recordAuditEventUseCase)
            .record(any(AuditEventCommand.class));

        assertThatThrownBy(() -> createRegionUseCase.create(
            actor.getUserId(),
            command(),
            UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage("audit storage failure");

        assertThat(regionRepository.count()).isZero();
        assertThat(auditEventRepository.count()).isZero();
    }

    private AppUser createPlatformAdmin() {
        return transactionTemplate.execute(status -> {
            String suffix = Long.toUnsignedString(System.nanoTime());
            AppUser actor = appUserRepository.save(new AppUser(
                "platform-admin-" + suffix + "@example.com",
                "hashed-password",
                "전체관리자",
                "010-1234-5678",
                AppUserAccountKind.PRIVILEGED,
                AppUserStatus.ACTIVE
            ));
            platformAdminAssignmentRepository.save(new PlatformAdminAssignment(
                actor,
                PlatformAdminGrade.PLATFORM_ADMIN
            ));
            return actor;
        });
    }

    private CreateRegionCommand command() {
        return new CreateRegionCommand(
            "JEONJU",
            "전주시",
            "PILOT_REGION_ADDITION",
            "OPS-2026-0805-REGION-03"
        );
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-09T00:00:00Z"), ZoneOffset.UTC);
        }
    }
}
