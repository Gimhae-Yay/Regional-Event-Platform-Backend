package io.regionevent.regioneventbackend.domain.visit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventActorLinkRepository;
import io.regionevent.regioneventbackend.domain.audit.repository.AuditEventRepository;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyRecordStatus;
import io.regionevent.regioneventbackend.domain.idempotency.repository.IdempotencyRecordRepository;
import io.regionevent.regioneventbackend.domain.mission.service.MissionProgressVisitCompletionAdapter;
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
@ExtendWith(OutputCaptureExtension.class)
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
    private final EntityManager entityManager;

    @MockitoBean
    private MissionProgressVisitCompletionAdapter missionProgressVisitCompletionAdapter;

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
        QrTokenService qrTokenService,
        EntityManager entityManager
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
        this.entityManager = entityManager;
    }

    @Test
    void manualCheckIn_succeedsAndReplaysSameIdempotencyResult() {
        Fixture fixture = createFixture();
        ManualCheckInRequest request = new ManualCheckInRequest(
            fixture.reservation().getReservationNo(),
            ManualCheckInReason.QR_SCAN_FAILED.name()
        );
        UUID firstRequestId = UUID.randomUUID();

        CheckInResult firstResult = checkInUseCase.checkInManually(
            fixture.operator().getUserId(),
            request,
            "manual-key",
            firstRequestId
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
                assertThat(auditEvent.getRegion().getRegionId())
                    .isEqualTo(fixture.reservation().getRegion().getRegionId());
                assertThat(auditEvent.getTargetId()).isEqualTo(Long.valueOf(firstResult.response().visitId()));
                assertThat(auditEvent.getReasonCode()).isEqualTo("MANUAL_CHECK_IN_QR_SCAN_FAILED_SUCCESS");
                assertThat(auditEvent.getPreviousState()).isEqualTo(ReservationStatus.CONFIRMED.name());
                assertThat(auditEvent.getNextState()).isEqualTo(ReservationStatus.CHECKED_IN.name());
            });
        List<Long> successAuditEventIds = auditEventRepository.findAll()
            .stream()
            .filter(auditEvent -> auditEvent.getResult() == AuditEventResult.SUCCESS)
            .filter(auditEvent -> auditEvent.getTargetType() == AuditEventTargetType.VISIT)
            .map(AuditEvent::getAuditEventId)
            .toList();
        assertThat(auditEventActorLinkRepository.findAll())
            .filteredOn(actorLink -> successAuditEventIds.contains(actorLink.getAuditEventId()))
            .singleElement()
            .satisfies(actorLink -> assertThat(actorLink.getActor().getUserId())
                .isEqualTo(fixture.operator().getUserId()));
        verify(missionProgressVisitCompletionAdapter).recordAfterCommit(
            Long.valueOf(firstResult.response().visitId()),
            firstRequestId
        );
        verifyNoMoreInteractions(missionProgressVisitCompletionAdapter);
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
    void manualCheckIn_withSameKeyAndDifferentReservation_returnsKeyConflictWithoutCreatingAdditionalVisit() {
        Fixture firstFixture = createFixture();
        Fixture secondFixture = createFixtureForOperator(
            firstFixture.operator(),
            firstFixture.reservation().getRegion()
        );
        ManualCheckInRequest firstRequest = new ManualCheckInRequest(
            firstFixture.reservation().getReservationNo(),
            ManualCheckInReason.QR_SCAN_FAILED.name()
        );
        ManualCheckInRequest differentReservationRequest = new ManualCheckInRequest(
            secondFixture.reservation().getReservationNo(),
            ManualCheckInReason.QR_SCAN_FAILED.name()
        );

        CheckInResult firstResult = checkInUseCase.checkInManually(
            firstFixture.operator().getUserId(),
            firstRequest,
            "manual-different-reservation-key",
            UUID.randomUUID()
        );
        CheckInResult conflictResult = checkInUseCase.checkInManually(
            firstFixture.operator().getUserId(),
            differentReservationRequest,
            "manual-different-reservation-key",
            UUID.randomUUID()
        );

        assertThat(firstResult.isSuccessful()).isTrue();
        assertThat(conflictResult.isSuccessful()).isFalse();
        assertThat(conflictResult.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        assertThat(visitRepository.count()).isEqualTo(1);
        assertThat(reservationRepository.findById(secondFixture.reservation().getReservationId()))
            .hasValueSatisfying(reservation -> assertThat(reservation.getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void manualCheckIn_whenOperatorHasDifferentRegion_throwsForbiddenWithoutCreatingVisit() {
        Fixture fixture = createFixture();
        String suffix = Long.toUnsignedString(System.nanoTime());
        Region otherRegion = regionRepository.saveAndFlush(new Region("OR" + suffix, "Other Region", true));
        AppUser otherRegionOperator = saveUser("operator-other-region-" + suffix);
        UserRoleAssignment otherRegionAssignment = userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            otherRegionOperator,
            UserRole.OPERATOR,
            otherRegion
        ));
        DatabaseSnapshot before = snapshot(fixture.reservation());
        UUID requestId = UUID.randomUUID();

        try {
            assertThatThrownBy(() -> checkInUseCase.checkInManually(
                otherRegionOperator.getUserId(),
                new ManualCheckInRequest(
                    fixture.reservation().getReservationNo(),
                    ManualCheckInReason.QR_SCAN_FAILED.name()
                ),
                "manual-region-forbidden-key",
                requestId
            ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
                );

            assertThat(visitRepository.count()).isZero();
            assertThat(snapshot(fixture.reservation())).isEqualTo(before);
            assertThat(auditEventsByRequestId(requestId))
                .singleElement()
                .satisfies(auditEvent -> {
                    assertThat(auditEvent.getRegion().getRegionId())
                        .isEqualTo(fixture.reservation().getRegion().getRegionId());
                    assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.RESERVATION);
                    assertThat(auditEvent.getTargetId()).isEqualTo(fixture.reservation().getReservationId());
                    assertThat(auditEvent.getReasonCode())
                        .isEqualTo("MANUAL_CHECK_IN_QR_SCAN_FAILED_REGION_FORBIDDEN");
                });
        } finally {
            deleteAuditEvents(requestId);
            userRoleAssignmentRepository.delete(otherRegionAssignment);
            appUserRepository.delete(otherRegionOperator);
            regionRepository.delete(otherRegion);
            deleteFixture(fixture);
        }
    }

    @Test
    void manualCheckIn_whenOperatorDoesNotOwnContent_throwsForbiddenWithoutCreatingVisit() {
        Fixture fixture = createFixture();
        String suffix = Long.toUnsignedString(System.nanoTime());
        AppUser sameRegionOperator = saveUser("operator-other-owner-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            sameRegionOperator,
            UserRole.OPERATOR,
            fixture.reservation().getRegion()
        ));
        DatabaseSnapshot before = snapshot(fixture.reservation());

        assertThatThrownBy(() -> checkInUseCase.checkInManually(
            sameRegionOperator.getUserId(),
            new ManualCheckInRequest(
                fixture.reservation().getReservationNo(),
                ManualCheckInReason.QR_SCAN_FAILED.name()
            ),
            "manual-owner-forbidden-key",
            UUID.randomUUID()
        ))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        assertThat(visitRepository.count()).isZero();
        assertThat(snapshot(fixture.reservation())).isEqualTo(before);
    }

    @Test
    void manualCheckIn_whenUserIsRegionAdmin_throwsForbiddenWithoutManualRoleAuditReason() {
        Fixture fixture = createFixture();
        String suffix = Long.toUnsignedString(System.nanoTime());
        AppUser regionAdmin = saveUser("region-admin-manual-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(
            regionAdmin,
            UserRole.REGION_ADMIN,
            fixture.reservation().getRegion()
        ));
        UUID requestId = UUID.randomUUID();

        assertThatThrownBy(() -> checkInUseCase.checkInManually(
            regionAdmin.getUserId(),
            new ManualCheckInRequest(
                fixture.reservation().getReservationNo(),
                ManualCheckInReason.QR_SCAN_FAILED.name()
            ),
            "manual-region-admin-forbidden-key",
            requestId
        ))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        assertThat(visitRepository.count()).isZero();
        assertThat(auditEventsByRequestId(requestId)).isEmpty();
    }

    @Test
    void manualCheckIn_whenInputIsInvalid_doesNotChangeReservationVisitOrIdempotency() {
        Fixture fixture = createFixture();
        DatabaseSnapshot before = snapshot(fixture.reservation());

        assertThatThrownBy(() -> checkInUseCase.checkInManually(
            fixture.operator().getUserId(),
            new ManualCheckInRequest(
                fixture.reservation().getReservationNo(),
                "INVALID_REASON"
            ),
            "manual-invalid-reason-key",
            UUID.randomUUID()
        ))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
            );

        assertThat(snapshot(fixture.reservation())).isEqualTo(before);
    }

    @Test
    void manualCheckIn_whenOperatorRoleIsMissing_doesNotChangeReservationVisitOrIdempotency() {
        Fixture fixture = createFixture();
        String suffix = Long.toUnsignedString(System.nanoTime());
        AppUser visitor = saveUser("manual-no-operator-role-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(visitor, UserRole.VISITOR, null));
        DatabaseSnapshot before = snapshot(fixture.reservation());

        assertThatThrownBy(() -> checkInUseCase.checkInManually(
            visitor.getUserId(),
            new ManualCheckInRequest(
                fixture.reservation().getReservationNo(),
                ManualCheckInReason.QR_SCAN_FAILED.name()
            ),
            "manual-no-operator-role-key",
            UUID.randomUUID()
        ))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        assertThat(snapshot(fixture.reservation())).isEqualTo(before);
    }

    @Test
    void qrCheckIn_whenTokenIsInvalid_storesFailedIdempotencyResult() {
        Fixture fixture = createFixture();
        QrCheckInRequest request = new QrCheckInRequest("invalid-token");
        UUID requestId = UUID.randomUUID();

        CheckInResult firstResult = checkInUseCase.checkInByQr(
            fixture.operator().getUserId(),
            request,
            "qr-failure-key",
            requestId
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
        assertThat(auditEventsByRequestId(requestId))
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getRegion().getRegionId())
                    .isEqualTo(fixture.reservation().getRegion().getRegionId());
                assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.RESERVATION);
                assertThat(auditEvent.getTargetId()).isNull();
                assertThat(auditEvent.getReasonCode()).isEqualTo("QR_CHECK_IN_MALFORMED");
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
        CheckInResult rescanResult = checkInUseCase.checkInByQr(
            fixture.operator().getUserId(),
            new QrCheckInRequest(qrToken.token()),
            "qr-rescan-key",
            UUID.randomUUID()
        );

        assertThat(result.isSuccessful()).isTrue();
        assertThat(rescanResult.isSuccessful()).isTrue();
        assertThat(rescanResult.response().visitId()).isEqualTo(result.response().visitId());
        assertThat(visitRepository.count()).isEqualTo(1);
        assertThat(auditEventsByRequestId(requestId))
            .singleElement()
            .satisfies(auditEvent -> {
                assertThat(auditEvent.getResult()).isEqualTo(AuditEventResult.SUCCESS);
                assertThat(auditEvent.getTargetType()).isEqualTo(AuditEventTargetType.VISIT);
                assertThat(auditEvent.getPreviousState()).isEqualTo(ReservationStatus.CONFIRMED.name());
                assertThat(auditEvent.getNextState()).isEqualTo(ReservationStatus.CHECKED_IN.name());
            });
        verify(missionProgressVisitCompletionAdapter).recordAfterCommit(
            Long.valueOf(result.response().visitId()),
            requestId
        );
        verifyNoMoreInteractions(missionProgressVisitCompletionAdapter);
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
        long visitCount = visitRepository.count();
        long idempotencyRecordCount = idempotencyRecordRepository.count();

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
            assertThat(visitRepository.count()).isEqualTo(visitCount);
            assertThat(idempotencyRecordRepository.count()).isEqualTo(idempotencyRecordCount);
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
    void manualCheckIn_whenReservationHasNoMember_returnsConflictWithoutCreatingVisit() {
        Fixture fixture = createFixtureWithoutUser();

        assertManualConflict(
            fixture,
            "manual-member-unlinked-key",
            "MANUAL_CHECK_IN_QR_SCAN_FAILED_MEMBER_UNLINKED"
        );
    }

    @Test
    void manualCheckIn_whenReservationIsExpired_returnsConflictWithoutCreatingVisit() {
        Fixture fixture = createFixture(ReservationStatus.EXPIRED);

        assertManualConflict(
            fixture,
            "manual-reservation-expired-key",
            "MANUAL_CHECK_IN_QR_SCAN_FAILED_RESERVATION_EXPIRED"
        );
    }

    @Test
    void manualCheckIn_whenSessionIsCancelled_returnsConflictWithoutCreatingVisit() {
        Fixture fixture = createFixture();
        fixture.reservation().getContentSession().cancel(
            fixture.operator(),
            Instant.now(),
            "operator cancelled"
        );
        contentSessionRepository.saveAndFlush(fixture.reservation().getContentSession());

        assertManualConflict(
            fixture,
            "manual-session-cancelled-key",
            "MANUAL_CHECK_IN_QR_SCAN_FAILED_SESSION_CANCELLED"
        );
    }

    @Test
    void manualCheckIn_whenSessionIsCompleted_returnsConflictWithoutCreatingVisit() {
        Fixture fixture = createFixture();
        fixture.reservation().getContentSession().complete(Instant.now());
        contentSessionRepository.saveAndFlush(fixture.reservation().getContentSession());

        assertManualConflict(
            fixture,
            "manual-session-completed-key",
            "MANUAL_CHECK_IN_QR_SCAN_FAILED_SESSION_COMPLETED"
        );
    }

    @Test
    void manualCheckIn_whenCheckInWindowIsNotOpen_returnsConflictWithoutCreatingVisit() {
        Fixture fixture = createFixtureWithCheckInWindow(
            Instant.now().plusSeconds(600),
            Instant.now().plusSeconds(1_200)
        );

        assertManualConflict(
            fixture,
            "manual-window-not-open-key",
            "MANUAL_CHECK_IN_QR_SCAN_FAILED_WINDOW_NOT_OPEN"
        );
    }

    @Test
    void manualCheckIn_whenCheckInWindowIsClosed_returnsConflictWithoutCreatingVisit() {
        Fixture fixture = createFixtureWithCheckInWindow(
            Instant.now().minusSeconds(1_200),
            Instant.now().minusSeconds(600)
        );

        assertManualConflict(
            fixture,
            "manual-window-closed-key",
            "MANUAL_CHECK_IN_QR_SCAN_FAILED_WINDOW_CLOSED"
        );
    }

    @Test
    void manualCheckIn_whenExistingVisitRelationDoesNotMatch_throwsInternalServerError() {
        Fixture fixture = createFixture(ReservationStatus.CHECKED_IN);
        Visit visit = saveVisit(fixture);
        ContentSession otherSession = saveSessionForContent(
            fixture.reservation().getContentSession().getContent(),
            fixture.reservation().getRegion(),
            fixture.operator(),
            Instant.now().minusSeconds(600),
            Instant.now().plusSeconds(3_600),
            Instant.now().minusSeconds(60),
            Instant.now().plusSeconds(600)
        );
        FlushModeType previousFlushMode = entityManager.getFlushMode();
        entityManager.setFlushMode(FlushModeType.COMMIT);
        ReflectionTestUtils.setField(visit, "contentSession", otherSession);

        try {
            assertThatThrownBy(() -> checkInUseCase.checkInManually(
                fixture.operator().getUserId(),
                new ManualCheckInRequest(
                    fixture.reservation().getReservationNo(),
                    ManualCheckInReason.QR_SCAN_FAILED.name()
                ),
                "manual-visit-relation-inconsistent-key",
                UUID.randomUUID()
            ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
                );
        } finally {
            ReflectionTestUtils.setField(visit, "contentSession", fixture.reservation().getContentSession());
            entityManager.setFlushMode(previousFlushMode);
        }
    }

    @Test
    void manualCheckIn_whenSessionIsPending_returnsConflictWithoutCreatingVisit() {
        Fixture fixture = createFixtureWithPendingSession();

        assertManualConflict(
            fixture,
            "manual-pending-session-key",
            "MANUAL_CHECK_IN_QR_SCAN_FAILED_STATE_TRANSITION_CONFLICT"
        );
    }

    @Test
    void manualCheckIn_whenConflictOccurs_doesNotExposeRawPersonalData(
        CapturedOutput output
    ) {
        Fixture fixture = createFixture(ReservationStatus.CANCELLED);
        String reservationNo = fixture.reservation().getReservationNo();
        String loginIdentifier = fixture.reservation().getUser().getLoginIdentifier();
        String name = fixture.reservation().getUser().getName();
        String phone = fixture.reservation().getUser().getPhone();
        String operatorUserIdLogField = "userId=" + fixture.operator().getUserId();

        CheckInResult result = checkInUseCase.checkInManually(
            fixture.operator().getUserId(),
            new ManualCheckInRequest(reservationNo, ManualCheckInReason.QR_SCAN_FAILED.name()),
            "manual-pii-log-key",
            UUID.randomUUID()
        );

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.errorCode()).isEqualTo(ErrorCode.CHECK_IN_CONFLICT);
        assertThat(auditEventRepository.findAll())
            .extracting(AuditEvent::getReasonCode)
            .doesNotContain(reservationNo, loginIdentifier, name, phone);
        assertThat(idempotencyRecordRepository.findAll())
            .extracting(record -> record.getRequestHash())
            .doesNotContain(reservationNo, loginIdentifier, name, phone);
        assertThat(output.getOut())
            .doesNotContain(reservationNo, loginIdentifier, name, phone, operatorUserIdLogField);
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
        return createFixture(reservationStatus, true);
    }

    private Fixture createFixtureWithPendingSession() {
        return createFixture(ReservationStatus.CONFIRMED, false);
    }

    private Fixture createFixtureWithoutUser() {
        Instant now = Instant.now();
        return createFixture(
            ReservationStatus.CONFIRMED,
            true,
            false,
            now.minusSeconds(600),
            now.plusSeconds(3_600),
            now.minusSeconds(60),
            now.plusSeconds(600)
        );
    }

    private Fixture createFixtureWithCheckInWindow(
        Instant checkinOpenAt,
        Instant checkinCloseAt
    ) {
        return createFixture(
            ReservationStatus.CONFIRMED,
            true,
            true,
            checkinOpenAt.plusSeconds(300),
            checkinCloseAt.plusSeconds(600),
            checkinOpenAt,
            checkinCloseAt
        );
    }

    private Fixture createFixture(
        ReservationStatus reservationStatus,
        boolean approveSession
    ) {
        Instant now = Instant.now();
        return createFixture(
            reservationStatus,
            approveSession,
            true,
            now.minusSeconds(600),
            now.plusSeconds(3_600),
            now.minusSeconds(60),
            now.plusSeconds(600)
        );
    }

    private Fixture createFixture(
        ReservationStatus reservationStatus,
        boolean approveSession,
        boolean linkUser,
        Instant startsAt,
        Instant endsAt,
        Instant checkinOpenAt,
        Instant checkinCloseAt
    ) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        Region region = regionRepository.saveAndFlush(new Region("R" + suffix, "김해시", true));
        AppUser operator = saveUser("operator-" + suffix);
        userRoleAssignmentRepository.saveAndFlush(new UserRoleAssignment(operator, UserRole.OPERATOR, region));
        AppUser user = linkUser ? saveUser("visitor-" + suffix) : null;
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
            startsAt,
            endsAt,
            checkinOpenAt,
            checkinCloseAt,
            10
        );
        if (approveSession) {
            session.approve(operator, now.minusSeconds(300));
        }
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
            cancelledAt(reservationStatus, now),
            cancellationReason(reservationStatus),
            expiredAt(reservationStatus, now),
            null
        ));
        return new Fixture(operator, reservation);
    }

    private Fixture createFixtureForOperator(
        AppUser operator,
        Region region
    ) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        Instant now = Instant.now();
        AppUser user = saveUser("visitor-same-operator-" + suffix);
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
            "간단한 복장",
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
            ReservationStatus.CONFIRMED,
            now,
            null,
            null,
            null,
            null
        ));
        return new Fixture(operator, reservation);
    }

    private ContentSession saveSessionForContent(
        Content content,
        Region region,
        AppUser operator,
        Instant startsAt,
        Instant endsAt,
        Instant checkinOpenAt,
        Instant checkinCloseAt
    ) {
        ContentSession session = new ContentSession(
            content,
            region,
            startsAt,
            endsAt,
            checkinOpenAt,
            checkinCloseAt,
            10
        );
        session.approve(operator, Instant.now());
        return contentSessionRepository.saveAndFlush(session);
    }

    private Instant cancelledAt(
        ReservationStatus reservationStatus,
        Instant now
    ) {
        if (reservationStatus == ReservationStatus.CANCELLED) {
            return now.plusSeconds(60);
        }
        return null;
    }

    private String cancellationReason(ReservationStatus reservationStatus) {
        if (reservationStatus == ReservationStatus.CANCELLED) {
            return "USER_REQUEST";
        }
        return null;
    }

    private Instant expiredAt(
        ReservationStatus reservationStatus,
        Instant now
    ) {
        if (reservationStatus == ReservationStatus.EXPIRED) {
            return now.plusSeconds(60);
        }
        return null;
    }

    private void assertManualConflict(
        Fixture fixture,
        String idempotencyKey,
        String expectedReasonCode
    ) {
        ReservationStatus previousStatus = fixture.reservation().getStatus();
        long succeededIdempotencyRecordCount = countSucceededIdempotencyRecords();
        UUID requestId = UUID.randomUUID();

        CheckInResult result = checkInUseCase.checkInManually(
            fixture.operator().getUserId(),
            new ManualCheckInRequest(
                fixture.reservation().getReservationNo(),
                ManualCheckInReason.QR_SCAN_FAILED.name()
            ),
            idempotencyKey,
            requestId
        );

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.errorCode()).isEqualTo(ErrorCode.CHECK_IN_CONFLICT);
        assertThat(visitsByReservationId(fixture.reservation().getReservationId())).isEmpty();
        assertThat(reservationRepository.findById(fixture.reservation().getReservationId()))
            .hasValueSatisfying(reservation -> assertThat(reservation.getStatus()).isEqualTo(previousStatus));
        assertThat(countSucceededIdempotencyRecords()).isEqualTo(succeededIdempotencyRecordCount);
        assertThat(auditEventsByRequestId(requestId))
            .singleElement()
            .satisfies(auditEvent -> assertThat(auditEvent.getReasonCode()).isEqualTo(expectedReasonCode));
    }

    private DatabaseSnapshot snapshot(Reservation reservation) {
        ReservationStatus reservationStatus = reservationRepository.findById(reservation.getReservationId())
            .orElseThrow()
            .getStatus();
        return new DatabaseSnapshot(
            reservationStatus,
            visitsByReservationId(reservation.getReservationId()).size(),
            idempotencyRecordRepository.count(),
            countSucceededIdempotencyRecords()
        );
    }

    private long countSucceededIdempotencyRecords() {
        return idempotencyRecordRepository.findAll()
            .stream()
            .filter(record -> record.getStatus() == IdempotencyRecordStatus.SUCCEEDED)
            .count();
    }

    private List<Visit> visitsByReservationId(Long reservationId) {
        return visitRepository.findAll()
            .stream()
            .filter(visit -> visit.getReservation().getReservationId().equals(reservationId))
            .toList();
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

    private void deleteFixture(Fixture fixture) {
        Reservation reservation = fixture.reservation();
        Long operatorUserId = fixture.operator().getUserId();
        AppUser visitor = reservation.getUser();
        ContentSession contentSession = reservation.getContentSession();
        Content content = contentSession.getContent();
        Region region = reservation.getRegion();
        userRoleAssignmentRepository.findAll()
            .stream()
            .filter(assignment -> assignment.getAppUser().getUserId().equals(operatorUserId))
            .forEach(userRoleAssignmentRepository::delete);
        reservationRepository.delete(reservation);
        capacityHoldRepository.delete(reservation.getCapacityHold());
        contentSessionRepository.delete(contentSession);
        contentRepository.delete(content);
        appUserRepository.delete(visitor);
        appUserRepository.delete(fixture.operator());
        regionRepository.delete(region);
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

    private record DatabaseSnapshot(
        ReservationStatus reservationStatus,
        int visitCount,
        long idempotencyRecordCount,
        long succeededIdempotencyRecordCount
    ) {
    }
}
