package io.regionevent.regioneventbackend.domain.idempotency.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyOperation;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecord;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecordStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.repository.RegionRepository;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.CapacityHoldRepository;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class IdempotencyRecordRepositoryTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-02T00:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-02T00:01:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-03T00:00:00Z");
    private static final Instant HOLD_TERMINAL_AT = Instant.parse("2026-08-01T23:59:00Z");
    private static final Instant CHECKED_AT = Instant.parse("2026-08-02T01:05:00Z");

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final RegionRepository regionRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final AppUserRepository appUserRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final VisitRepository visitRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    IdempotencyRecordRepositoryTest(
        IdempotencyRecordRepository idempotencyRecordRepository,
        RegionRepository regionRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        AppUserRepository appUserRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        VisitRepository visitRepository,
        EntityManager entityManager,
        JdbcTemplate jdbcTemplate
    ) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.regionRepository = regionRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.appUserRepository = appUserRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.visitRepository = visitRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 멱등_기록은_처리자와_작업별_결과를_지연_로딩으로_연결한다() {
        IdempotencyFixtures fixtures = createFixtures();
        IdempotencyRecord reservationRecord = idempotencyRecordRepository.saveAndFlush(
            newReservationRecord(fixtures.actor(), "reservation-key", fixtures.reservation())
        );
        IdempotencyRecord visitRecord = idempotencyRecordRepository.saveAndFlush(
            newVisitRecord(fixtures.actor(), "visit-key", fixtures.visit())
        );
        entityManager.clear();

        IdempotencyRecord foundReservationRecord = idempotencyRecordRepository.findById(
            reservationRecord.getIdempotencyRecordId()
        ).orElseThrow();
        IdempotencyRecord foundVisitRecord = idempotencyRecordRepository.findById(
            visitRecord.getIdempotencyRecordId()
        ).orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        assertThat(foundReservationRecord.getOperation()).isEqualTo(IdempotencyOperation.RESERVATION_CONFIRM);
        assertThat(foundReservationRecord.getStatus()).isEqualTo(IdempotencyRecordStatus.SUCCEEDED);
        assertThat(foundReservationRecord.getResultCode()).isEqualTo("RESERVATION_CONFIRMED");
        assertThat(persistenceUnitUtil.isLoaded(foundReservationRecord, "actor")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(foundReservationRecord, "resultReservation")).isFalse();
        assertThat(foundReservationRecord.getResultReservation().getReservationId())
            .isEqualTo(fixtures.reservation().getReservationId());
        assertThat(foundReservationRecord.getResultVisit()).isNull();
        assertThat(persistenceUnitUtil.isLoaded(foundVisitRecord, "resultVisit")).isFalse();
        assertThat(foundVisitRecord.getResultVisit().getVisitId()).isEqualTo(fixtures.visit().getVisitId());
        assertThat(foundVisitRecord.getResultReservation()).isNull();
    }

    @Test
    void 멱등_기록은_처리자가_없고_멱등_키가_없는_처리_중_명령도_저장한다() {
        insertRecord(
            null,
            IdempotencyOperation.CHECK_IN.name(),
            null,
            "jdbc-request-hash",
            IdempotencyRecordStatus.PROCESSING.name(),
            null,
            null
        );
        IdempotencyRecord savedRecord = idempotencyRecordRepository.saveAndFlush(new IdempotencyRecord(
            null,
            IdempotencyOperation.CHECK_IN,
            null,
            "request-hash",
            IdempotencyRecordStatus.PROCESSING,
            null,
            null,
            null,
            CREATED_AT,
            null,
            EXPIRES_AT
        ));
        entityManager.clear();

        IdempotencyRecord foundRecord = idempotencyRecordRepository.findById(
            savedRecord.getIdempotencyRecordId()
        ).orElseThrow();

        assertThat(foundRecord.getActor()).isNull();
        assertThat(foundRecord.getIdempotencyKeyHash()).isNull();
        assertThat(foundRecord.getStatus()).isEqualTo(IdempotencyRecordStatus.PROCESSING);
        assertThat(foundRecord.getCompletedAt()).isNull();
        assertThat(foundRecord.getExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void 멱등_기록은_처리자와_명령과_키_해시의_조합을_유일하게_보장한다() {
        IdempotencyFixtures fixtures = createFixtures();
        idempotencyRecordRepository.saveAndFlush(new IdempotencyRecord(
            fixtures.actor(),
            IdempotencyOperation.RESERVATION_CONFIRM,
            "same-key",
            "request-hash-1",
            IdempotencyRecordStatus.PROCESSING,
            null,
            null,
            null,
            CREATED_AT,
            null,
            EXPIRES_AT
        ));

        assertThatThrownBy(() -> insertRecord(
            fixtures.actor(),
            IdempotencyOperation.RESERVATION_CONFIRM.name(),
            "same-key",
            "request-hash-2",
            IdempotencyRecordStatus.PROCESSING.name(),
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 멱등_기록은_허용된_명령과_상태에_맞는_결과만_저장한다() {
        IdempotencyFixtures fixtures = createFixtures();

        assertThatThrownBy(() -> insertRecord(
            fixtures.actor(),
            "CANCEL",
            "invalid-operation",
            "request-hash",
            IdempotencyRecordStatus.PROCESSING.name(),
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRecord(
            fixtures.actor(),
            IdempotencyOperation.RESERVATION_CONFIRM.name(),
            "missing-reservation-result",
            "request-hash",
            IdempotencyRecordStatus.SUCCEEDED.name(),
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> new IdempotencyRecord(
            fixtures.actor(),
            IdempotencyOperation.CHECK_IN,
            "wrong-result",
            "request-hash",
            IdempotencyRecordStatus.SUCCEEDED,
            "CHECKED_IN",
            fixtures.reservation(),
            null,
            CREATED_AT,
            COMPLETED_AT,
            EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 같은_예약_결과는_서로_다른_멱등_기록에서_재사용할_수_있다() {
        IdempotencyFixtures fixtures = createFixtures();
        IdempotencyRecord firstRecord = idempotencyRecordRepository.saveAndFlush(
            newReservationRecord(fixtures.actor(), "first-key", fixtures.reservation())
        );
        IdempotencyRecord secondRecord = idempotencyRecordRepository.saveAndFlush(
            newReservationRecord(fixtures.actor(), "second-key", fixtures.reservation())
        );
        entityManager.clear();

        IdempotencyRecord foundFirstRecord = idempotencyRecordRepository.findById(
            firstRecord.getIdempotencyRecordId()
        ).orElseThrow();
        IdempotencyRecord foundSecondRecord = idempotencyRecordRepository.findById(
            secondRecord.getIdempotencyRecordId()
        ).orElseThrow();

        assertThat(foundFirstRecord.getResultReservation().getReservationId())
            .isEqualTo(fixtures.reservation().getReservationId());
        assertThat(foundSecondRecord.getResultReservation().getReservationId())
            .isEqualTo(fixtures.reservation().getReservationId());
    }

    @Test
    void 만료된_종결_기록만_삭제한다() {
        IdempotencyFixtures fixtures = createFixtures();
        Instant now = Instant.now();
        IdempotencyRecord expiredFailedRecord = idempotencyRecordRepository.saveAndFlush(new IdempotencyRecord(
            fixtures.actor(),
            IdempotencyOperation.RESERVATION_CONFIRM,
            "expired-failed-key",
            "expired-failed-request-hash",
            IdempotencyRecordStatus.FAILED,
            "RESERVATION_CONFIRM_CONFLICT",
            null,
            null,
            now.minusSeconds(86401),
            now.minusSeconds(86400),
            now.minusSeconds(1)
        ));
        IdempotencyRecord expiredProcessingRecord = idempotencyRecordRepository.saveAndFlush(new IdempotencyRecord(
            fixtures.actor(),
            IdempotencyOperation.CHECK_IN,
            "expired-processing-key",
            "expired-processing-request-hash",
            IdempotencyRecordStatus.PROCESSING,
            null,
            null,
            null,
            now.minusSeconds(86401),
            null,
            now.minusSeconds(1)
        ));
        IdempotencyRecord unexpiredFailedRecord = idempotencyRecordRepository.saveAndFlush(new IdempotencyRecord(
            fixtures.actor(),
            IdempotencyOperation.RESERVATION_CONFIRM,
            "unexpired-failed-key",
            "unexpired-failed-request-hash",
            IdempotencyRecordStatus.FAILED,
            "RESERVATION_CONFIRM_CONFLICT",
            null,
            null,
            now,
            now,
            now.plusSeconds(86400)
        ));

        int deletedCount = idempotencyRecordRepository.deleteExpiredTerminalRecords(List.of(
            IdempotencyRecordStatus.SUCCEEDED,
            IdempotencyRecordStatus.FAILED
        ));
        entityManager.clear();

        assertThat(deletedCount).isEqualTo(1);
        assertThat(idempotencyRecordRepository.findById(expiredFailedRecord.getIdempotencyRecordId())).isEmpty();
        assertThat(idempotencyRecordRepository.findById(expiredProcessingRecord.getIdempotencyRecordId())).isPresent();
        assertThat(idempotencyRecordRepository.findById(unexpiredFailedRecord.getIdempotencyRecordId())).isPresent();
    }

    private IdempotencyRecord newReservationRecord(
        AppUser actor,
        String idempotencyKeyHash,
        Reservation reservation
    ) {
        return new IdempotencyRecord(
            actor,
            IdempotencyOperation.RESERVATION_CONFIRM,
            idempotencyKeyHash,
            "reservation-request-hash-" + idempotencyKeyHash,
            IdempotencyRecordStatus.SUCCEEDED,
            "RESERVATION_CONFIRMED",
            reservation,
            null,
            CREATED_AT,
            COMPLETED_AT,
            EXPIRES_AT
        );
    }

    private IdempotencyRecord newVisitRecord(
        AppUser actor,
        String idempotencyKeyHash,
        Visit visit
    ) {
        return new IdempotencyRecord(
            actor,
            IdempotencyOperation.CHECK_IN,
            idempotencyKeyHash,
            "visit-request-hash-" + idempotencyKeyHash,
            IdempotencyRecordStatus.SUCCEEDED,
            "CHECKED_IN",
            null,
            visit,
            CREATED_AT,
            COMPLETED_AT,
            EXPIRES_AT
        );
    }

    private void insertRecord(
        AppUser actor,
        String operation,
        String idempotencyKeyHash,
        String requestHash,
        String status,
        Reservation resultReservation,
        Visit resultVisit
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO idempotency_record (
                    actor_user_id,
                    operation,
                    idempotency_key_hash,
                    request_hash,
                    status,
                    result_reservation_id,
                    result_visit_id,
                    created_at,
                    expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            actor == null ? null : actor.getUserId(),
            operation,
            idempotencyKeyHash,
            requestHash,
            status,
            resultReservation == null ? null : resultReservation.getReservationId(),
            resultVisit == null ? null : resultVisit.getVisitId(),
            Timestamp.from(CREATED_AT),
            Timestamp.from(EXPIRES_AT)
        );
    }

    private IdempotencyFixtures createFixtures() {
        Region region = regionRepository.saveAndFlush(new Region("GIMHAE", "김해시", true));
        AppUser actor = saveUser("visitor@example.com", "예약 사용자");
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            saveUser("operator@example.com", "운영자"),
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 가야 문화 체험",
            "김해 가야 문화를 체험하는 행사입니다.",
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안전요원의 안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "시작 하루 전까지 취소할 수 있습니다.",
            Instant.parse("2026-08-01T00:00:00Z")
        ));
        ContentSession contentSession = contentSessionRepository.saveAndFlush(new ContentSession(
            content,
            region,
            Instant.parse("2026-08-02T01:00:00Z"),
            Instant.parse("2026-08-02T03:00:00Z"),
            Instant.parse("2026-08-02T00:30:00Z"),
            Instant.parse("2026-08-02T02:30:00Z"),
            20
        ));
        CapacityHold capacityHold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            contentSession,
            actor,
            1,
            CapacityHoldStatus.CONSUMED,
            CREATED_AT,
            HOLD_TERMINAL_AT,
            null,
            null
        ));
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "R-20260802-001",
            "qr-reference-001",
            region,
            capacityHold,
            contentSession,
            actor,
            ReservationStatus.CONFIRMED,
            CREATED_AT,
            null,
            null,
            null,
            null
        ));
        Visit visit = visitRepository.saveAndFlush(new Visit(
            region,
            reservation,
            actor,
            content,
            contentSession,
            saveUser("checkin-operator@example.com", "체크인 처리자"),
            CheckinMethod.QR,
            CHECKED_AT
        ));
        return new IdempotencyFixtures(actor, reservation, visit);
    }

    private AppUser saveUser(String loginIdentifier, String name) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier,
            "hashed-password",
            name,
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    private record IdempotencyFixtures(
        AppUser actor,
        Reservation reservation,
        Visit visit
    ) {
    }
}
