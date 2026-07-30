package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecord;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecordStatus;
import io.regionevent.regioneventbackend.domain.idempotency.repository.IdempotencyRecordRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:reservation-confirmation;MODE=MySQL;DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReservationConfirmationServiceTest {

    private final ReservationConfirmationService reservationConfirmationService;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;

    @Autowired
    ReservationConfirmationServiceTest(
        ReservationConfirmationService reservationConfirmationService,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        IdempotencyRecordRepository idempotencyRecordRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository
    ) {
        this.reservationConfirmationService = reservationConfirmationService;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
    }

    @Test
    void confirm_활성_홀드를_소비하고_예약과_멱등_성공_및_감사_이벤트를_저장한다() {
        ReservationFixtures fixtures = createFixtures();
        int remainingCapacityBeforeConfirmation = fixtures.contentSession().getRemainingCapacity();

        ReservationConfirmationResponse response = reservationConfirmationService.confirm(
            fixtures.user().getUserId(),
            fixtures.capacityHold().getHoldId(),
            "confirmation-key-1",
            "e56cbd4c-0cbe-4a5e-a5f5-3e4b649fd1c1"
        );

        CapacityHold consumedHold = capacityHoldRepository.findById(fixtures.capacityHold().getHoldId()).orElseThrow();
        List<AuditEvent> auditEvents = auditEventRepository.findAll();
        IdempotencyRecord idempotencyRecord = idempotencyRecordRepository.findAll().getFirst();

        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(response.reservationNo()).matches("R\\d{8}[0-9A-HJKMNPQRSTVWXYZ]{12}");
        assertThat(response.reservationId()).isNotBlank();
        assertThat(consumedHold.getStatus()).isEqualTo(CapacityHoldStatus.CONSUMED);
        assertThat(consumedHold.getTerminalAt()).isNotNull();
        assertThat(contentSessionRepository.findById(fixtures.contentSession().getSessionId()).orElseThrow().getRemainingCapacity())
            .isEqualTo(remainingCapacityBeforeConfirmation);
        assertThat(idempotencyRecord.getStatus()).isEqualTo(IdempotencyRecordStatus.SUCCEEDED);
        assertThat(idempotencyRecord.getCompletedAt()).isNotNull();
        assertThat(idempotencyRecord.getExpiresAt()).isEqualTo(
            idempotencyRecord.getCompletedAt().plus(Duration.ofHours(24))
        );
        assertThat(auditEvents)
            .extracting(AuditEvent::getTargetType)
            .containsExactlyInAnyOrder(AuditEventTargetType.CAPACITY_HOLD, AuditEventTargetType.RESERVATION);
        assertThat(auditEvents).allSatisfy(event -> assertThat(event.getResult()).isEqualTo(AuditEventResult.SUCCESS));
        assertThat(auditEventActorLinkRepository.count()).isEqualTo(2);
    }

    @Test
    void confirm_같은_멱등_키와_홀드로_재시도하면_저장된_성공_결과를_반환한다() {
        ReservationFixtures fixtures = createFixtures();

        ReservationConfirmationResponse firstResponse = reservationConfirmationService.confirm(
            fixtures.user().getUserId(),
            fixtures.capacityHold().getHoldId(),
            "confirmation-key-2",
            "0e000f39-3b3e-4309-aa32-444a238e43a5"
        );
        ReservationConfirmationResponse retryResponse = reservationConfirmationService.confirm(
            fixtures.user().getUserId(),
            fixtures.capacityHold().getHoldId(),
            "confirmation-key-2",
            "00455f1e-a6bd-4df0-87b4-1e9110ac7cd2"
        );

        assertThat(retryResponse).isEqualTo(firstResponse);
        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(1);
        assertThat(auditEventRepository.count()).isEqualTo(2);
    }

    @Test
    void confirm_다른_키로_소비된_홀드를_확정하면_실패_결과를_저장한다() {
        ReservationFixtures fixtures = createFixtures();
        reservationConfirmationService.confirm(
            fixtures.user().getUserId(),
            fixtures.capacityHold().getHoldId(),
            "confirmation-key-3-first",
            "54f1f2a0-10d0-4ae7-bc76-8c9a00aaf3d0"
        );

        assertThatThrownBy(() -> reservationConfirmationService.confirm(
            fixtures.user().getUserId(),
            fixtures.capacityHold().getHoldId(),
            "confirmation-key-3-second",
            "6f568438-c172-4563-9c16-39a48c0d9750"
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.RESERVATION_CONFIRM_CONFLICT);

        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(idempotencyRecordRepository.findAll())
            .extracting(IdempotencyRecord::getStatus)
            .containsExactlyInAnyOrder(IdempotencyRecordStatus.SUCCEEDED, IdempotencyRecordStatus.FAILED);
        assertThat(auditEventRepository.count()).isEqualTo(3);
    }

    @Test
    void confirm_같은_멱등_키를_다른_홀드에_사용하면_충돌로_거부한다() {
        ReservationFixtures fixtures = createFixtures();
        CapacityHold anotherCapacityHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            fixtures.region(),
            fixtures.contentSession(),
            fixtures.user(),
            1,
            CapacityHoldStatus.ACTIVE,
            Instant.now().plusSeconds(600),
            null,
            null,
            null
        ));
        reservationConfirmationService.confirm(
            fixtures.user().getUserId(),
            fixtures.capacityHold().getHoldId(),
            "confirmation-key-4",
            "91d59c84-5133-42a0-a0ec-4847d84984b3"
        );

        assertThatThrownBy(() -> reservationConfirmationService.confirm(
            fixtures.user().getUserId(),
            anotherCapacityHold.getHoldId(),
            "confirmation-key-4",
            "5c86d294-272c-42e4-bc96-a369946b6b4b"
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);

        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(1);
        assertThat(capacityHoldRepository.findById(anotherCapacityHold.getHoldId()).orElseThrow().getStatus())
            .isEqualTo(CapacityHoldStatus.ACTIVE);
        assertThat(auditEventRepository.findAll())
            .filteredOn(event -> ErrorCode.IDEMPOTENCY_KEY_CONFLICT.code().equals(event.getReasonCode()))
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getResult()).isEqualTo(AuditEventResult.FAILURE);
                assertThat(event.getTargetType()).isEqualTo(AuditEventTargetType.CAPACITY_HOLD);
                assertThat(event.getTargetId()).isEqualTo(anotherCapacityHold.getHoldId());
            });
        assertThat(auditEventActorLinkRepository.count()).isEqualTo(3);
    }

    private ReservationFixtures createFixtures() {
        String suffix = UUID.randomUUID().toString();
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE" + suffix.substring(0, 4), "김해시", true));
        AppUser user = saveUser("visitor-" + suffix + "@example.com");
        AppUser operator = saveUser("operator-" + suffix + "@example.com");
        Instant now = Instant.now();
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-1234-5678",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            now.minusSeconds(60)
        ));
        ContentSession contentSession = contentSessionRepository.saveAndFlush(new ContentSession(
            content,
            region,
            now.plusSeconds(3600),
            now.plusSeconds(11400),
            now.plusSeconds(1800),
            now.plusSeconds(10800),
            20
        ));
        CapacityHold capacityHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            contentSession,
            user,
            2,
            CapacityHoldStatus.ACTIVE,
            now.plusSeconds(600),
            null,
            null,
            null
        ));
        return new ReservationFixtures(region, user, contentSession, capacityHold);
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            "예약 사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private record ReservationFixtures(
        Region region,
        AppUser user,
        ContentSession contentSession,
        CapacityHold capacityHold
    ) {
    }
}
