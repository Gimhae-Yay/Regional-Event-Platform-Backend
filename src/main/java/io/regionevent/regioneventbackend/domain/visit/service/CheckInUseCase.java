package io.regionevent.regioneventbackend.domain.visit.service;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailureAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.idempotency.entity.IdempotencyOperation;
import io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyAcquireResult;
import io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyCommand;
import io.regionevent.regioneventbackend.domain.idempotency.service.IdempotencyService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.domain.visit.dto.CheckInResponse;
import io.regionevent.regioneventbackend.domain.visit.dto.ManualCheckInRequest;
import io.regionevent.regioneventbackend.domain.visit.dto.QrCheckInRequest;
import io.regionevent.regioneventbackend.domain.visit.entity.CheckinMethod;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.security.qr.QrTokenService;

@Service
public class CheckInUseCase {

    private static final Logger log = LoggerFactory.getLogger(CheckInUseCase.class);
    private static final String SUCCESS_RESULT_CODE = "SUCCESS";

    private final AppUserService appUserService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final ReservationService reservationService;
    private final VisitService visitService;
    private final IdempotencyService idempotencyService;
    private final CheckInHasher checkInHasher;
    private final QrTokenService qrTokenService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailureAuditEventUseCase recordFailureAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;

    public CheckInUseCase(
        AppUserService appUserService,
        UserRoleAssignmentService userRoleAssignmentService,
        ReservationService reservationService,
        VisitService visitService,
        IdempotencyService idempotencyService,
        CheckInHasher checkInHasher,
        QrTokenService qrTokenService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailureAuditEventUseCase recordFailureAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase
    ) {
        this.appUserService = appUserService;
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.reservationService = reservationService;
        this.visitService = visitService;
        this.idempotencyService = idempotencyService;
        this.checkInHasher = checkInHasher;
        this.qrTokenService = qrTokenService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailureAuditEventUseCase = recordFailureAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
    }

    @Transactional
    public CheckInResult checkInByQr(
        Long userId,
        QrCheckInRequest request,
        String idempotencyKey,
        UUID requestId
    ) {
        String validatedIdempotencyKey = validateIdempotencyKey(idempotencyKey);
        AppUser operator = appUserService.findActiveUser(userId);
        AuditEventActor actor;
        try {
            actor = new AuditEventActor(userRoleAssignmentService.findActiveOperator(userId));
        } catch (BusinessException exception) {
            recordRollbackFailure(
                requestId,
                findAuditActorOrNull(userId),
                null,
                AuditEventTargetType.RESERVATION,
                null,
                null,
                "QR_CHECK_IN_OPERATOR_ROLE_FORBIDDEN"
            );
            throw exception;
        }
        IdempotencyAcquireResult acquireResult = acquire(
            operator,
            validatedIdempotencyKey,
            checkInHasher.hashQrRequest(request.qrToken())
        );

        CheckInResult existingResult = toExistingResult(acquireResult, requestId, actor);
        if (existingResult != null) {
            return existingResult;
        }

        IdempotencyAcquireResult.Acquired acquired = (IdempotencyAcquireResult.Acquired) acquireResult;
        Instant checkedAt = reservationService.findCurrentDatabaseInstant();
        QrTokenService.VerificationResult verificationResult = qrTokenService.verify(request.qrToken(), checkedAt);
        if (verificationResult instanceof QrTokenService.Rejected rejected) {
            return completeDeterministicFailure(
                acquired,
                requestId,
                actor,
                null,
                null,
                null,
                qrFailureReasonCode(rejected.failure()),
                ErrorCode.QR_VERIFICATION_FAILED
            );
        }

        QrTokenService.Verified verified = (QrTokenService.Verified) verificationResult;
        Reservation reservation = reservationService.findByQrReferenceForCheckIn(
            verified.claims().qrReference()
        ).orElse(null);
        if (reservation == null) {
            return completeDeterministicFailure(
                acquired,
                requestId,
                actor,
                null,
                null,
                null,
                "QR_CHECK_IN_REFERENCE_INVALID",
                ErrorCode.QR_VERIFICATION_FAILED
            );
        }
        if (!reservation.getContentSession().getSessionId().equals(verified.claims().sessionId())) {
            return completeDeterministicFailure(
                acquired,
                requestId,
                actor,
                reservation.getRegion(),
                AuditEventTargetType.RESERVATION,
                reservation.getReservationId(),
                "QR_CHECK_IN_SESSION_MISMATCH",
                ErrorCode.QR_VERIFICATION_FAILED
            );
        }

        return checkInReservation(
            acquired,
            reservation,
            operator,
            actor,
            requestId,
            CheckinMethod.QR,
            "QR_CHECK_IN",
            null,
            true,
            checkedAt
        );
    }

    @Transactional
    public CheckInResult checkInManually(
        Long userId,
        ManualCheckInRequest request,
        String idempotencyKey,
        UUID requestId
    ) {
        String validatedIdempotencyKey = validateIdempotencyKey(idempotencyKey);
        ManualCheckInReason reason = ManualCheckInReason.from(request.reason());
        AppUser operator = appUserService.findActiveUser(userId);
        AuditEventActor actor = new AuditEventActor(userRoleAssignmentService.findActiveOperator(userId));
        Reservation reservation = reservationService.findByReservationNoForCheckIn(request.reservationNo())
            .orElse(null);
        if (reservation == null) {
            recordRollbackFailure(
                requestId,
                actor,
                actor.roleAssignment().getRegion(),
                AuditEventTargetType.RESERVATION,
                null,
                null,
                "MANUAL_CHECK_IN_" + reason.name() + "_NOT_FOUND"
            );
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        IdempotencyAcquireResult acquireResult = acquire(
            operator,
            validatedIdempotencyKey,
            checkInHasher.hashManualRequest(reservation.getReservationId(), reason)
        );

        CheckInResult existingResult = toExistingResult(acquireResult, requestId, actor);
        if (existingResult != null) {
            return existingResult;
        }

        return checkInReservation(
            (IdempotencyAcquireResult.Acquired) acquireResult,
            reservation,
            operator,
            actor,
            requestId,
            CheckinMethod.RESERVATION_NUMBER,
            "MANUAL_CHECK_IN_" + reason.name(),
            "MANUAL_CHECK_IN_" + reason.name() + "_SUCCESS",
            false,
            reservationService.findCurrentDatabaseInstant()
        );
    }

    private IdempotencyAcquireResult acquire(
        AppUser operator,
        String idempotencyKey,
        String requestHash
    ) {
        return idempotencyService.acquire(new IdempotencyCommand(
            operator,
            IdempotencyOperation.CHECK_IN,
            checkInHasher.hashIdempotencyKey(idempotencyKey),
            requestHash
        ));
    }

    private CheckInResult toExistingResult(
        IdempotencyAcquireResult acquireResult,
        UUID requestId,
        AuditEventActor actor
    ) {
        if (acquireResult instanceof IdempotencyAcquireResult.Succeeded succeeded) {
            Visit visit = visitService.findByIdForCheckInResult(succeeded.record().getResultVisit().getVisitId());
            return CheckInResult.success(CheckInResponse.from(visit));
        }
        if (acquireResult instanceof IdempotencyAcquireResult.Failed failed) {
            return CheckInResult.failure(ErrorCode.fromCode(failed.record().getResultCode()));
        }
        if (acquireResult instanceof IdempotencyAcquireResult.KeyConflict) {
            recordFailure(requestId, actor, null, null, null, null, ErrorCode.IDEMPOTENCY_KEY_CONFLICT.code());
            return CheckInResult.failure(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        if (acquireResult instanceof IdempotencyAcquireResult.InProgress) {
            recordFailure(requestId, actor, null, null, null, null, ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS.code());
            return CheckInResult.failure(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        }
        return null;
    }

    private CheckInResult checkInReservation(
        IdempotencyAcquireResult.Acquired acquired,
        Reservation reservation,
        AppUser operator,
        AuditEventActor actor,
        UUID requestId,
        CheckinMethod method,
        String reasonCodePrefix,
        String successReasonCode,
        boolean allowQrRescan,
        Instant checkedAt
    ) {
        String authorizationFailureReasonCode = findAuthorizationFailureReasonCode(
            reservation,
            operator,
            actor.roleAssignment(),
            reasonCodePrefix
        );
        if (authorizationFailureReasonCode != null) {
            recordRollbackFailure(
                requestId,
                actor,
                reservation,
                reservation.getStatus().name(),
                authorizationFailureReasonCode
            );
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Visit existingVisit = visitService.findByReservationId(reservation.getReservationId()).orElse(null);
        if (existingVisit != null) {
            if (!hasMatchingVisitRelations(reservation, existingVisit)) {
                return completeConsistencyFailure(
                    requestId,
                    actor,
                    reservation,
                    reservation.getStatus().name(),
                    reasonCodePrefix + "_VISIT_INCONSISTENT"
                );
            }
            if (allowQrRescan) {
                String rescanConflictReasonCode = findQrRescanConflictReasonCode(
                    reservation,
                    reasonCodePrefix,
                    checkedAt
                );
                if (rescanConflictReasonCode != null) {
                    return completeConflict(
                        acquired,
                        requestId,
                        actor,
                        reservation,
                        reservation.getStatus().name(),
                        rescanConflictReasonCode
                    );
                }
                return completeSuccess(acquired, requestId, actor, existingVisit, successReasonCode);
            }
            return completeConflict(
                acquired,
                requestId,
                actor,
                reservation,
                reservation.getStatus().name(),
                reasonCodePrefix + "_RESERVATION_ALREADY_CHECKED_IN"
            );
        }

        if (reservation.getStatus() == ReservationStatus.CHECKED_IN) {
            return completeConsistencyFailure(
                requestId,
                actor,
                reservation,
                reservation.getStatus().name(),
                reasonCodePrefix + "_VISIT_INCONSISTENT"
            );
        }

        String conflictReasonCode = findConflictReasonCode(reservation, reasonCodePrefix, checkedAt);
        if (conflictReasonCode != null) {
            return completeConflict(
                acquired,
                requestId,
                actor,
                reservation,
                reservation.getStatus().name(),
                conflictReasonCode
            );
        }

        if (!reservationService.checkInIfConfirmed(reservation.getReservationId())) {
            Reservation currentReservation = reservationService.findByIdForCheckInResult(reservation.getReservationId());
            return completeConflict(
                acquired,
                requestId,
                actor,
                currentReservation,
                currentReservation.getStatus().name(),
                reasonCodePrefix + "_STATE_TRANSITION_CONFLICT"
            );
        }

        Reservation checkedReservation = reservationService.findByIdForCheckInResult(reservation.getReservationId());
        Visit visit = visitService.create(new Visit(
            checkedReservation.getRegion(),
            checkedReservation,
            checkedReservation.getUser(),
            checkedReservation.getContentSession().getContent(),
            checkedReservation.getContentSession(),
            operator,
            method,
            checkedAt
        ));
        return completeSuccess(acquired, requestId, actor, visit, successReasonCode);
    }

    private CheckInResult completeSuccess(
        IdempotencyAcquireResult.Acquired acquired,
        UUID requestId,
        AuditEventActor actor,
        Visit visit,
        String reasonCode
    ) {
        idempotencyService.completeWithVisit(acquired.record(), SUCCESS_RESULT_CODE, visit);
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            visit.getRegion(),
            AuditEventTargetType.VISIT,
            visit.getVisitId(),
            null,
            "CHECKED_IN",
            AuditEventResult.SUCCESS,
            reasonCode,
            actor,
            visit.getCheckedAt()
        ));
        return CheckInResult.success(CheckInResponse.from(visit));
    }

    private CheckInResult completeConflict(
        IdempotencyAcquireResult.Acquired acquired,
        UUID requestId,
        AuditEventActor actor,
        Reservation reservation,
        String previousState,
        String reasonCode
    ) {
        return completeDeterministicFailure(
            acquired,
            requestId,
            actor,
            reservation.getRegion(),
            AuditEventTargetType.RESERVATION,
            reservation.getReservationId(),
            reasonCode,
            ErrorCode.CHECK_IN_CONFLICT,
            previousState
        );
    }

    private CheckInResult completeConsistencyFailure(
        UUID requestId,
        AuditEventActor actor,
        Reservation reservation,
        String previousState,
        String reasonCode
    ) {
        recordRollbackFailure(requestId, actor, reservation, previousState, reasonCode);
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private CheckInResult completeDeterministicFailure(
        IdempotencyAcquireResult.Acquired acquired,
        UUID requestId,
        AuditEventActor actor,
        Region region,
        AuditEventTargetType targetType,
        Long targetId,
        String reasonCode,
        ErrorCode errorCode
    ) {
        return completeDeterministicFailure(
            acquired,
            requestId,
            actor,
            region,
            targetType,
            targetId,
            reasonCode,
            errorCode,
            null
        );
    }

    private CheckInResult completeDeterministicFailure(
        IdempotencyAcquireResult.Acquired acquired,
        UUID requestId,
        AuditEventActor actor,
        Region region,
        AuditEventTargetType targetType,
        Long targetId,
        String reasonCode,
        ErrorCode errorCode,
        String previousState
    ) {
        idempotencyService.completeWithFailure(acquired.record(), errorCode.code());
        recordFailure(requestId, actor, region, targetType, targetId, previousState, reasonCode);
        log.warn(
            "Check-in rejected. requestId={}, userId={}, errorCode={}, reasonCode={}",
            requestId,
            actor.getAppUser().getUserId(),
            errorCode.code(),
            reasonCode
        );
        return CheckInResult.failure(errorCode);
    }

    private void recordRollbackFailure(
        UUID requestId,
        AuditEventActor actor,
        Reservation reservation,
        String previousState,
        String reasonCode
    ) {
        recordRollbackFailure(
            requestId,
            actor,
            reservation.getRegion(),
            AuditEventTargetType.RESERVATION,
            reservation.getReservationId(),
            previousState,
            reasonCode
        );
    }

    private void recordRollbackFailure(
        UUID requestId,
        AuditEventActor actor,
        Region region,
        AuditEventTargetType targetType,
        Long targetId,
        String previousState,
        String reasonCode
    ) {
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            region,
            targetType,
            targetId,
            previousState,
            null,
            AuditEventResult.FAILURE,
            reasonCode,
            actor,
            Instant.now()
        ));
    }

    private void recordFailure(
        UUID requestId,
        AuditEventActor actor,
        Region region,
        AuditEventTargetType targetType,
        Long targetId,
        String previousState,
        String reasonCode
    ) {
        recordFailureAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            region,
            targetType == null ? AuditEventTargetType.RESERVATION : targetType,
            targetId,
            previousState,
            null,
            AuditEventResult.FAILURE,
            reasonCode,
            actor,
            Instant.now()
        ));
    }

    private AuditEventActor findAuditActorOrNull(Long userId) {
        return userRoleAssignmentService.findRoleAssignmentsByUserId(userId)
            .stream()
            .findFirst()
            .map(AuditEventActor::new)
            .orElse(null);
    }

    private String findAuthorizationFailureReasonCode(
        Reservation reservation,
        AppUser operator,
        UserRoleAssignment operatorAssignment,
        String reasonCodePrefix
    ) {
        Long operatorRegionId = operatorAssignment.getRegion().getRegionId();
        if (!reservation.getRegion().getRegionId().equals(operatorRegionId)
            || !reservation.getContentSession().getContent().isScopedTo(operatorRegionId)) {
            return reasonCodePrefix + "_REGION_FORBIDDEN";
        }
        if (!reservation.getContentSession().getContent().isOwnedBy(operator.getUserId())) {
            return reasonCodePrefix + "_OWNER_FORBIDDEN";
        }
        return null;
    }

    private boolean hasMatchingVisitRelations(Reservation reservation, Visit visit) {
        return sameId(reservation.getReservationId(), visit.getReservation().getReservationId())
            && sameId(reservation.getRegion().getRegionId(), visit.getRegion().getRegionId())
            && sameId(
                reservation.getContentSession().getContent().getContentId(),
                visit.getContent().getContentId()
            )
            && sameId(reservation.getContentSession().getSessionId(), visit.getContentSession().getSessionId());
    }

    private String findQrRescanConflictReasonCode(
        Reservation reservation,
        String reasonCodePrefix,
        Instant checkedAt
    ) {
        if (reservation.getUser() == null) {
            return reasonCodePrefix + "_MEMBER_UNLINKED";
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return reasonCodePrefix + "_RESERVATION_CANCELLED";
        }
        if (reservation.getStatus() == ReservationStatus.EXPIRED) {
            return reasonCodePrefix + "_RESERVATION_EXPIRED";
        }
        if (reservation.getStatus() != ReservationStatus.CHECKED_IN) {
            return reasonCodePrefix + "_STATE_TRANSITION_CONFLICT";
        }
        return findSessionConflictReasonCode(reservation, reasonCodePrefix, checkedAt);
    }

    private String findConflictReasonCode(
        Reservation reservation,
        String reasonCodePrefix,
        Instant checkedAt
    ) {
        if (reservation.getUser() == null) {
            return reasonCodePrefix + "_MEMBER_UNLINKED";
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return reasonCodePrefix + "_RESERVATION_CANCELLED";
        }
        if (reservation.getStatus() == ReservationStatus.EXPIRED) {
            return reasonCodePrefix + "_RESERVATION_EXPIRED";
        }
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            return reasonCodePrefix + "_STATE_TRANSITION_CONFLICT";
        }
        return findSessionConflictReasonCode(reservation, reasonCodePrefix, checkedAt);
    }

    private String findSessionConflictReasonCode(
        Reservation reservation,
        String reasonCodePrefix,
        Instant checkedAt
    ) {
        ContentSession contentSession = reservation.getContentSession();
        if (contentSession.getStatus() == ContentSessionStatus.CANCELLED) {
            return reasonCodePrefix + "_SESSION_CANCELLED";
        }
        if (contentSession.getStatus() == ContentSessionStatus.COMPLETED) {
            return reasonCodePrefix + "_SESSION_COMPLETED";
        }
        if (contentSession.getStatus() != ContentSessionStatus.SCHEDULED) {
            return reasonCodePrefix + "_SESSION_NOT_SCHEDULED";
        }
        if (checkedAt.isBefore(contentSession.getCheckinOpenAt())) {
            return reasonCodePrefix + "_WINDOW_NOT_OPEN";
        }
        if (!checkedAt.isBefore(contentSession.getCheckinCloseAt())) {
            return reasonCodePrefix + "_WINDOW_CLOSED";
        }
        return null;
    }

    private String qrFailureReasonCode(QrTokenService.VerificationFailure failure) {
        return switch (failure) {
            case MALFORMED -> "QR_CHECK_IN_MALFORMED";
            case VERSION_UNSUPPORTED -> "QR_CHECK_IN_VERSION_UNSUPPORTED";
            case KEY_UNKNOWN -> "QR_CHECK_IN_KEY_UNKNOWN";
            case SIGNATURE_INVALID -> "QR_CHECK_IN_SIGNATURE_INVALID";
            case EXPIRED -> "QR_CHECK_IN_EXPIRED";
        };
    }

    private boolean sameId(Long expected, Long actual) {
        return expected != null && expected.equals(actual);
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return idempotencyKey;
    }
}
