package io.regionevent.regioneventbackend.domain.visit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecordStatus;
import io.regionevent.regioneventbackend.domain.idempotency.repository.IdempotencyRecordRepository;
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
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.domain.visit.dto.ManualCheckInRequest;
import io.regionevent.regioneventbackend.domain.visit.dto.QrCheckInRequest;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.repository.VisitRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.security.qr.QrTokenService;
@SpringBootTest
@Transactional
@Sql(statements = """
    CREATE ALIAS IF NOT EXISTS UNIX_TIMESTAMP FOR "io.regionevent.regioneventbackend.domain.visit.service.CheckInUseCaseIntegrationTest.unixTimestamp"
    """)
public class CheckInUseCaseIntegrationTest {

    private final CheckInUseCase checkInUseCase;
    private final RegionRepository regionRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final ContentRepository contentRepository;
    private final ContentSessionRepository contentSessionRepository;
    private final CapacityHoldRepository capacityHoldRepository;
    private final ReservationRepository reservationRepository;
    private final VisitRepository visitRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventActorLinkRepository auditEventActorLinkRepository;
    private final QrTokenService qrTokenService;

    @Autowired
    CheckInUseCaseIntegrationTest(
        CheckInUseCase checkInUseCase,
        RegionRepository regionRepository,
        AppUserRepository appUserRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        ContentRepository contentRepository,
        ContentSessionRepository contentSessionRepository,
        CapacityHoldRepository capacityHoldRepository,
        ReservationRepository reservationRepository,
        VisitRepository visitRepository,
        IdempotencyRecordRepository idempotencyRecordRepository,
        AuditEventRepository auditEventRepository,
        AuditEventActorLinkRepository auditEventActorLinkRepository,
        QrTokenService qrTokenService
    ) {
        this.checkInUseCase = checkInUseCase;
        this.regionRepository = regionRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.contentRepository = contentRepository;
        this.contentSessionRepository = contentSessionRepository;
        this.capacityHoldRepository = capacityHoldRepository;
        this.reservationRepository = reservationRepository;
        this.visitRepository = visitRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditEventActorLinkRepository = auditEventActorLinkRepository;
        this.qrTokenService = qrTokenService;
    }

    @Test
    void manualCheckIn_succeedsAndReplaysSameIdempotencyResult() {
        Fixture fixture = createFixture();
        ManualCheckInRequest request = new ManualCheckInRequest(
            fixture.reservation().getReservationNo(),
            ManualCheckInReason.QR_SCAN_FAILED.name()
        );

        CheckInResult firstResult = checkInUseCase.checkInManually(
            fixture.operator().getUserId(),
            request,
            "manual-key",
            UUID.randomUUID()
        );
        CheckInResult retryResult = checkInUseCase.checkInManually(
            fixture.operator().getUserId(),
            request,
            "manual-key",
            UUID.randomUUID()
        );

        assertThat(firstResult.isSuccessful()).isTrue();
        assertThat(retryResult.isSuccessful()).isTrue();
        assertThat(retryResult.response().visitId()).isEqualTo(firstResult.response().visitId());
        assertThat(retryResult.response().reservationStatus()).isEqualTo(ReservationStatus.CHECKED_IN.name());
        assertThat(retryResult.response().checkInMethod()).isEqualTo(CheckinMethod.RESERVATION_NUMBER.name());
        assertThat(visitRepository.count()).isEqualTo(1);
        assertThat(reservationRepository.findById(fixture.reservation().getReservationId()))
            .hasValueSatisfying(reservation -> assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CHECKED_IN));
        assertThat(idempotencyRecordRepository.findAll())
            .singleElement()
            .satisfies(record -> {
                assertThat(record.getStatus()).isEqualTo(IdempotencyRecordStatus.SUCCEEDED);
                assertThat(record.getResultVisit().getVisitId().toString()).isEqualTo(firstResult.response().visitId());
            });
        assertThat(auditEventRepository.findAll())
            .filteredOn(auditEvent -> auditEvent.getResult() == AuditEventResult.SUCCESS)
            .filteredOn(auditEvent -> auditEvent.getTargetType() == AuditEventTargetType.VISIT)
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getPreviousState()).isEqualTo(ReservationStatus.CONFIRMED.name());
                assertThat(auditEvent.getNextState()).isEqualTo(ReservationStatus.CHECKED_IN.name());
            });
    }

    @Test
    void manualCheckIn_withSameKeyAndDifferentRequest_returnsKeyConflictWithoutCreatingVisit() {
        Fixture fixture = createFixture();
        ManualCheckInRequest firstRequest = new ManualCheckInRequest(
            fixture.reservation().getReservationNo(),
            ManualCheckInReason.QR_SCAN_FAILED.name()
        );
        ManualCheckInRequest differentRequest = new ManualCheckInRequest(
            fixture.reservation().getReservationNo(),
            ManualCheckInReason.QR_NOT_AVAILABLE.name()
        );

        CheckInResult firstResult = checkInUseCase.checkInManually(
            fixture.operator().getUserId(),
            firstRequest,
            "manual-conflict-key",
            UUID.randomUUID()
        );
        CheckInResult conflictResult = checkInUseCase.checkInManually(
            fixture.operator().getUserId(),
            differentRequest,
            "manual-conflict-key",
            UUID.randomUUID()
        );

        assertThat(firstResult.isSuccessful()).isTrue();
        assertThat(conflictResult.isSuccessful()).isFalse();
        assertThat(conflictResult.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        assertThat(visitRepository.count()).isEqualTo(1);
    }

    @Test
    void qrCheckIn_whenTokenIsInvalid_storesFailedIdempotencyResult() {
        Fixture fixture = createFixture();
        QrCheckInRequest request = new QrCheckInRequest("invalid-token");

        CheckInResult firstResult = checkInUseCase.checkInByQr(
            fixture.operator().getUserId(),
            request,
            "qr-failure-key",
            UUID.randomUUID()
        );
        CheckInResult retryResult = checkInUseCase.checkInByQr(
            fixture.operator().getUserId(),
            request,
            "qr-failure-key",
            UUID.randomUUID()
        );

        assertThat(firstResult.isSuccessful()).isFalse();
        assertThat(firstResult.errorCode()).isEqualTo(ErrorCode.QR_VERIFICATION_FAILED);
        assertThat(retryResult.isSuccessful()).isFalse();
        assertThat(retryResult.errorCode()).isEqualTo(ErrorCode.QR_VERIFICATION_FAILED);
        assertThat(visitRepository.count()).isZero();
        assertThat(idempotencyRecordRepository.findAll())
            .singleElement()
            .satisfies(record -> {
                assertThat(record.getStatus()).isEqualTo(IdempotencyRecordStatus.FAILED);
                assertThat(record.getResultCode()).isEqualTo(ErrorCode.QR_VERIFICATION_FAILED.code());
            });
    }

    @Test
    void qrCheckIn_succeedsAndRecordsSuccessAuditTransition() {
        Fixture fixture = createFixture();
        QrTokenService.IssuedQrToken qrToken = issueQrToken(fixture);
        UUID requestId = UUID.randomUUID();

        CheckInResult result = checkInUseCase.checkInByQr(
            fixture.operator().getUserId(),
            new QrCheckInRequest(qrToken.token()),
            "qr-success-audit-key",
            requestId
        );

        assertThat(result.isSuccessful()).isTrue();
        assertThat(auditEventsByRequestId(requestId))
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
                assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.VISIT);
                assertThat(auditEvent.getPreviousState()).isEqualTo(ReservationStatus.CONFIRMED.name());
                assertThat(auditEvent.getNextState()).isEqualTo(ReservationStatus.CHECKED_IN.name());
            });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void manualCheckIn_whenReservationNoDoesNotExist_recordsNotFoundAudit() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region region = regionRepository.saveAndFlush(new Region("NF" + suffix, "源?댁떆", true));
        AppUser operator = saveUser("operator-not-found-" + suffix);
        UserRoleAssignment assignment = userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(operator, UserRole.OPERATOR, region)
        );
        UUID requestId = UUID.randomUUID();

        try {
            assertThatThrownBy(() -> checkInUseCase.checkInManually(
                operator.getUserId(),
                new ManualCheckInRequest("missing-reservation-no", ManualCheckInReason.QR_NOT_AVAILABLE.name()),
                "manual-not-found-key",
                requestId
            ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
                );

            assertThat(auditEventsByRequestId(requestId))
                .singleElement()
                .satisfies(auditEvent -> {
                    assertThat(auditEvent.getRegion().getRegionId()).isEqualTo(region.getRegionId());
                    assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.RESERVATION);
                    assertThat(auditEvent.getTargetId()).isNull();
                    assertThat(auditEvent.getReasonCode())
                        .isEqualTo("MANUAL_CHECK_IN_QR_NOT_AVAILABLE_NOT_FOUND");
                });
        } finally {
            deleteAuditEvents(requestId);
            userRoleAssignmentRepository.delete(assignment);
            appUserRepository.delete(operator);
            regionRepository.delete(region);
        }
    }

    @Test
    void manualCheckIn_whenReservationAlreadyCheckedIn_returnsAlreadyCheckedInReason() {
        Fixture fixture = createFixture(ReservationStatus.CHECKED_IN);
        saveVisit(fixture);
        UUID requestId = UUID.randomUUID();

        CheckInResult result = checkInUseCase.checkInManually(
            fixture.operator().getUserId(),
            new ManualCheckInRequest(
                fixture.reservation().getReservationNo(),
                ManualCheckInReason.QR_SCAN_FAILED.name()
            ),
            "manual-already-checked-in-key",
            requestId
        );

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.errorCode()).isEqualTo(ErrorCode.CHECK_IN_CONFLICT);
        assertThat(auditEventsByRequestId(requestId))
            .singleElement()
            .satisfies(auditEvent ->
                assertThat(auditEvent.getReasonCode())
                    .isEqualTo("MANUAL_CHECK_IN_QR_SCAN_FAILED_RESERVATION_ALREADY_CHECKED_IN")
            );
    }

    @Test
    void manualCheckIn_whenCheckedInReservationHasNoVisit_throwsInternalServerError() {
        Fixture fixture = createFixture(ReservationStatus.CHECKED_IN);

        assertThatThrownBy(() -> checkInUseCase.checkInManually(
            fixture.operator().getUserId(),
            new ManualCheckInRequest(
                fixture.reservation().getReservationNo(),
                ManualCheckInReason.QR_SCAN_FAILED.name()
            ),
            "manual-visit-inconsistent-key",
            UUID.randomUUID()
        ))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
    }

    @Test
    void qrCheckIn_whenNewScanFindsExistingVisitButSessionIsCompleted_returnsConflict() {
        Fixture fixture = createFixture(ReservationStatus.CHECKED_IN);
        saveVisit(fixture);
        fixture.reservation().getContentSession().complete(Instant.now());
        contentSessionRepository.saveAndFlush(fixture.reservation().getContentSession());
        QrTokenService.IssuedQrToken qrToken = issueQrToken(fixture);
        UUID requestId = UUID.randomUUID();

        CheckInResult result = checkInUseCase.checkInByQr(
            fixture.operator().getUserId(),
            new QrCheckInRequest(qrToken.token()),
            "qr-existing-visit-completed-session-key",
            requestId
        );

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.errorCode()).isEqualTo(ErrorCode.CHECK_IN_CONFLICT);
        assertThat(auditEventsByRequestId(requestId))
            .singleElement()
            .satisfies(auditEvent ->
                assertThat(auditEvent.getReasonCode()).isEqualTo("QR_CHECK_IN_SESSION_COMPLETED")
            );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void qrCheckIn_whenUserHasNoOperatorRole_recordsOperatorRoleForbiddenAudit() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        AppUser visitor = saveUser("visitor-operator-forbidden-" + suffix);
        UserRoleAssignment assignment = userRoleAssignmentRepository.saveAndFlush(
            new UserRoleAssignment(visitor, UserRole.VISITOR, null)
        );
        UUID requestId = UUID.randomUUID();

        try {
            assertThatThrownBy(() -> checkInUseCase.checkInByQr(
                visitor.getUserId(),
                new QrCheckInRequest("invalid-token"),
                "qr-operator-role-forbidden-key",
                requestId
            ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
                );

            assertThat(auditEventsByRequestId(requestId))
                .singleElement()
                .satisfies(auditEvent -> {
                    assertThat(auditEvent.getRegion()).isNull();
                    assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.RESERVATION);
                    assertThat(auditEvent.getTargetId()).isNull();
                    assertThat(auditEvent.getReasonCode()).isEqualTo("QR_CHECK_IN_OPERATOR_ROLE_FORBIDDEN");
                });
        } finally {
            deleteAuditEvents(requestId);
            userRoleAssignmentRepository.delete(assignment);
            appUserRepository.delete(visitor);
        }
    }

    private Fixture createFixture() {
        return createFixture(ReservationStatus.CONFIRMED);
    }

    private Fixture createFixture(ReservationStatus reservationStatus) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser operator = saveUser("operator-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        AppUser user = saveUser("visitor-" + suffix);
        Content content = contentRepository.saveAndFlush(new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PUBLISHED,
            "김해 문화 체험",
            "김해 문화 체험 설명",
            "김해시",
            "10:00-18:00",
            "055-123-4567",
            "안전 안내",
            "만 7세 이상",
            "편한 복장",
            "취소 정책",
            now.minusSeconds(600)
        ));
        ContentSession session = new ContentSession(
            content,
            region,
            now.minusSeconds(600),
            now.plusSeconds(3_600),
            now.minusSeconds(60),
            now.plusSeconds(600),
            10
        );
        session.approve(operator, now.minusSeconds(300));
        session = contentSessionRepository.saveAndFlush(session);
        CapacityHold hold = capacityHoldRepository.saveAndFlush(new CapacityHold(
            region,
            session,
            user,
            1,
            CapacityHoldStatus.CONSUMED,
            now,
            now,
            null,
            null
        ));
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
            "R-" + suffix,
            UUID.randomUUID().toString(),
            region,
            hold,
            session,
            user,
            reservationStatus,
            now,
            null,
            null,
            null,
            null
        ));
        return new Fixture(operator, reservation);
    }

    private Visit saveVisit(Fixture fixture) {
        Reservation reservation = fixture.reservation();
        return visitRepository.saveAndFlush(new Visit(
            reservation.getRegion(),
            reservation,
            reservation.getUser(),
            reservation.getContentSession().getContent(),
            reservation.getContentSession(),
            fixture.operator(),
            CheckinMethod.RESERVATION_NUMBER,
            Instant.now()
        ));
    }

    private QrTokenService.IssuedQrToken issueQrToken(Fixture fixture) {
        Reservation reservation = fixture.reservation();
        return qrTokenService.issue(
            reservation.getQrReference(),
            reservation.getContentSession().getSessionId(),
            Instant.now(),
            reservation.getContentSession().getCheckinCloseAt()
        );
    }

    private List<AuditEvent> auditEventsByRequestId(UUID requestId) {
        return auditEventRepository.findAll()
            .stream()
            .filter(auditEvent -> auditEvent.getRequestId().equals(requestId.toString()))
            .toList();
    }

    private void deleteAuditEvents(UUID requestId) {
        List<Long> auditEventIds = auditEventsByRequestId(requestId)
            .stream()
            .map(AuditEvent::getAuditEventId)
            .toList();
        auditEventActorLinkRepository.findAll()
            .stream()
            .filter(actorLink -> auditEventIds.contains(actorLink.getAuditEventId()))
            .forEach(auditEventActorLinkRepository::delete);
        auditEventRepository.findAllById(auditEventIds)
            .forEach(auditEventRepository::delete);
    }

    private AppUser saveUser(String loginIdentifier) {
        return appUserRepository.saveAndFlush(new AppUser(
            loginIdentifier + "@example.com",
            "hashed-password",
            "사용자",
            "010-1234-5678",
            AppUserStatus.ACTIVE
        ));
    }

    public static BigDecimal unixTimestamp(OffsetDateTime value) {
        return BigDecimal.valueOf(value.toInstant().toEpochMilli())
            .movePointLeft(3);
    }

    private record Fixture(
        AppUser operator,
        Reservation reservation
    ) {
    }
}
