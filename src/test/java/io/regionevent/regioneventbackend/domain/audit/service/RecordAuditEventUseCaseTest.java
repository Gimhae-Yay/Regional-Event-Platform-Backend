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

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventActorLink;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;

@DataJpaTest
@Import({
    AuditEventService.class,
    AuditEventActorLinkService.class,
    RecordAuditEventUseCase.class
})
class RecordAuditEventUseCaseTest {

    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;

    @Autowired
    RecordAuditEventUseCaseTest(
        RecordAuditEventUseCase recordAuditEventUseCase,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        EntityManager entityManager
    ) {
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
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

        AuditEvent auditEvent = recordAuditEventUseCase.record(new AuditEventCommand(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            region,
            AuditEventTargetType.CONTENT,
            101L,
            "PENDING",
            "PUBLISHED",
            AuditEventResult.SUCCESS,
            "CONTENT_APPROVED",
            new AuditEventActor(actor, UserRole.REGION_ADMIN),
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
    void 감사_입력은_개인정보와_원문_형식의_사유를_거부한다() {
        assertThatIllegalArgumentException().isThrownBy(() -> createCommand("user@example.com"));
        assertThatIllegalArgumentException().isThrownBy(() -> createCommand("idempotency-key"));
        assertThatIllegalArgumentException().isThrownBy(() -> createCommand("eyJhbGciOiJIUzI1NiJ9.payload.signature"));
        assertThatIllegalArgumentException().isThrownBy(() -> createCommand("550e8400-e29b-41d4-a716-446655440000"));
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

        assertThatIllegalArgumentException().isThrownBy(
            () -> new AuditEventActor(withdrawingUser, UserRole.VISITOR)
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
