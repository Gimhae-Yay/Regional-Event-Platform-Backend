package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationService;
import io.regionevent.regioneventbackend.domain.payment.service.ExpirePendingPaymentForTerminatedHoldUseCase;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CancelContentSessionUseCase {

    private static final int MAX_CANCELLATION_REASON_LENGTH = 500;
    private static final String OPERATOR_SESSION_CANCEL_REASON = "OPERATOR_SESSION_CANCEL";

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final ContentSessionService contentSessionService;
    private final CapacityHoldService capacityHoldService;
    private final ExpirePendingPaymentForTerminatedHoldUseCase expirePendingPaymentForTerminatedHoldUseCase;
    private final ReservationService reservationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final Clock clock;

    public CancelContentSessionUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        ContentSessionService contentSessionService,
        CapacityHoldService capacityHoldService,
        ExpirePendingPaymentForTerminatedHoldUseCase expirePendingPaymentForTerminatedHoldUseCase,
        ReservationService reservationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        Clock clock
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.contentSessionService = contentSessionService;
        this.capacityHoldService = capacityHoldService;
        this.expirePendingPaymentForTerminatedHoldUseCase = expirePendingPaymentForTerminatedHoldUseCase;
        this.reservationService = reservationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public CancelContentSessionResult cancel(
        Long operatorUserId,
        Long sessionId,
        String cancellationReason,
        UUID requestId
    ) {
        String validatedReason = validateCancellationReason(cancellationReason);
        OperatorAuthorizationService.AuthorizedOperator operator = operatorAuthorizationService
            .requireAuthorizedOperator(operatorUserId);
        ContentSession contentSession = contentSessionService.findCancelTargetForUpdate(sessionId);
        validateOwnership(operator, contentSession);

        Instant cancelledAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ContentSession cancelledSession = contentSessionService.cancel(
            contentSession,
            operator.user(),
            cancelledAt,
            validatedReason
        );
        List<CapacityHoldService.TerminatedCapacityHold> terminatedHolds = capacityHoldService
            .invalidateActiveHoldsForSession(
            cancelledSession,
            validatedReason,
            cancelledAt
        );
        AuditEventActor actor = new AuditEventActor(operator.roleAssignment());
        terminatedHolds.forEach(capacityHold -> expirePendingPaymentForTerminatedHoldUseCase.expire(
            capacityHold,
            requestId,
            actor
        ));
        int releasedQuantity = terminatedHolds.stream()
            .mapToInt(CapacityHoldService.TerminatedCapacityHold::quantity)
            .sum() + reservationService.cancelUncheckedInReservationsForSession(
            cancelledSession,
            validatedReason,
            cancelledAt
        );
        contentSessionService.releaseCapacity(cancelledSession, releasedQuantity);
        recordSuccessfulAuditEvent(requestId, actor, cancelledSession, cancelledAt);

        return new CancelContentSessionResult(
            cancelledSession.getSessionId(),
            cancelledSession.getStatus(),
            cancelledSession.getCancellationReason(),
            cancelledSession.getCancelledAt()
        );
    }

    private void validateOwnership(
        OperatorAuthorizationService.AuthorizedOperator operator,
        ContentSession contentSession
    ) {
        if (!contentSession.getRegion().getRegionId().equals(operator.region().getRegionId())
            || !contentSession.getContent().isOwnedBy(operator.user().getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private String validateCancellationReason(String cancellationReason) {
        if (cancellationReason == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String trimmedReason = cancellationReason.trim();
        if (trimmedReason.isBlank() || trimmedReason.length() > MAX_CANCELLATION_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return trimmedReason;
    }

    private void recordSuccessfulAuditEvent(
        UUID requestId,
        AuditEventActor actor,
        ContentSession contentSession,
        Instant cancelledAt
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            contentSession.getRegion(),
            AuditEventTargetType.CONTENT_SESSION,
            contentSession.getSessionId(),
            "SCHEDULED",
            "CANCELLED",
            AuditEventResult.SUCCESS,
            OPERATOR_SESSION_CANCEL_REASON,
            actor,
            cancelledAt
        ));
    }
}
