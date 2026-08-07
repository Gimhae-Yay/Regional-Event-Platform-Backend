package io.regionevent.regioneventbackend.domain.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.transaction.TestTransaction;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventActorLink;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;

@DataJpaTest
@Import({
    AuditEventService.class,
    AuditEventActorLinkService.class,
    RecordAuditEventUseCase.class,
    RecordFailedAuditEventUseCase.class
})
class RecordAuditEventUseCaseTest {

    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final AuditEventRepository auditEventRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final EntityManager entityManager;

    @Autowired
    RecordAuditEventUseCaseTest(
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        AuditEventRepository auditEventRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        EntityManager entityManager
    ) {
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.auditEventRepository = auditEventRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.entityManager = entityManager;
    }

    @Test
    void 기록_활성_처리자가_있으면_이벤트와_처리자_연결을_저장한다() {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        AppUser actor = appUserRepository.saveAndFlush(new AppUser(
            "operator@example.com",
            "password-hash",
            "처리자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
        UserRoleAssignment roleAssignment = userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(actor, UserRole.REGION_ADMIN, region)
        );

        AuditEvent auditEvent = recordAuditEventUseCase.record(new AuditEventCommand(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            region,
            AuditEventTargetType.CONTENT,
            101L,
            "PENDING",
            "PUBLISHED",
            AuditEventResult.SUCCESS,
            "CONTENT_APPROVED",
            new AuditEventActor(roleAssignment),
            Instant.parse("2026-07-31T00:00:00Z")
        ));
        entityManager.flush();
        entityManager.clear();

        AuditEvent savedAuditEvent = entityManager.find(AuditEvent.class, auditEvent.getAuditEventId());
        AuditEventActorLink actorLink = auditEventActorLinkRepository
            .findById(auditEvent.getAuditEventId())
            .orElseThrow();

        assertThat(savedAuditEvent.getRequestId()).isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(savedAuditEvent.getActorKind()).isEqualTo("USER");
        assertThat(savedAuditEvent.getActorRole()).isEqualTo(UserRole.REGION_ADMIN.name());
        assertThat(actorLink.getActor().getUserId()).isEqualTo(actor.getUserId());
    }

    @Test
    void 기록_처리자가_없으면_시스템_이벤트만_저장한다() {
        AuditEvent auditEvent = recordAuditEventUseCase.record(new AuditEventCommand(
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            null,
            AuditEventTargetType.CAPACITY_HOLD,
            201L,
            "ACTIVE",
            "EXPIRED",
            AuditEventResult.SUCCESS,
            "HOLD_EXPIRED",
            null,
            Instant.parse("2026-07-31T00:00:00Z")
        ));
        entityManager.flush();
        entityManager.clear();

        AuditEvent savedAuditEvent = entityManager.find(AuditEvent.class, auditEvent.getAuditEventId());

        assertThat(savedAuditEvent.getActorKind()).isEqualTo("SYSTEM");
        assertThat(savedAuditEvent.getActorRole()).isNull();
        assertThat(auditEventActorLinkRepository.findById(auditEvent.getAuditEventId())).isEmpty();
    }

    @Test
    void 실패_기록은_호출_트랜잭션이_롤백되어도_독립적으로_보존한다() {
        recordFailedAuditEventUseCase.record(createCommand("RESERVATION_NOT_FOUND"));

        TestTransaction.flagForRollback();
        TestTransaction.end();

        assertThat(auditEventRepository.count()).isEqualTo(1);
    }

    @Test
    void 실패_기록은_호출_트랜잭션이_커밋되면_저장하지_않는다() {
        recordFailedAuditEventUseCase.record(createCommand("RESERVATION_NOT_FOUND"));

        TestTransaction.flagForCommit();
        TestTransaction.end();

        assertThat(auditEventRepository.count()).isZero();
    }

    @Test
    void 비활성_처리자는_감사_연결_입력으로_사용할_수_없다() {
        AppUser withdrawingUser = appUserRepository.saveAndFlush(new AppUser(
            "withdrawing@example.com",
            "password-hash",
            "탈퇴 처리 중 사용자",
            "010-9876-5432",
            AppUserStatus.WITHDRAWING
        ));
        UserRoleAssignment roleAssignment = userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(withdrawingUser, UserRole.VISITOR, null)
        );

        assertThatIllegalArgumentException().isThrownBy(
            () -> new AuditEventActor(roleAssignment)
        );
    }

    @Test
    void 실제로_부여되지_않은_역할은_감사_처리자_입력으로_사용할_수_없다() {
        AppUser appUser = appUserRepository.saveAndFlush(new AppUser(
            "visitor@example.com",
            "password-hash",
            "방문자",
            "010-2222-3333",
            AppUserStatus.ACTIVE
        ));
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(appUser, UserRole.VISITOR, null)
        );

        assertThatIllegalArgumentException().isThrownBy(
            () -> new AuditEventActor(
                new UserRoleAssignment(appUser, UserRole.REGION_ADMIN, region)
            )
        );
    }

    private AuditEventCommand createCommand(String reasonCode) {
        return new AuditEventCommand(
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            null,
            AuditEventTargetType.RESERVATION,
            null,
            null,
            null,
            AuditEventResult.FAILURE,
            reasonCode,
            null,
            Instant.parse("2026-07-31T00:00:00Z")
        );
    }
}
