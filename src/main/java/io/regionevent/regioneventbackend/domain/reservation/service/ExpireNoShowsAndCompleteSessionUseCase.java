package io.regionevent.regioneventbackend.domain.reservation.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;

@Service
public class ExpireNoShowsAndCompleteSessionUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExpireNoShowsAndCompleteSessionUseCase.class);
    private static final String NO_SHOW_REASON_CODE = "NO_SHOW";

    private final ContentSessionService contentSessionService;
    private final ReservationService reservationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final TransactionTemplate sessionTransactionTemplate;

    public ExpireNoShowsAndCompleteSessionUseCase(
        ContentSessionService contentSessionService,
        ReservationService reservationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        PlatformTransactionManager transactionManager
    ) {
        this.contentSessionService = contentSessionService;
        this.reservationService = reservationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        sessionTransactionTemplate = new TransactionTemplate(transactionManager);
        sessionTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public NoShowAndSessionCompletionResult execute() {
        UUID requestId = UUID.randomUUID();
        List<Long> sessionIds = contentSessionService.findNoShowProcessingTargetSessionIds();
        int expiredReservationCount = 0;
        int completedSessionCount = 0;
        int failedSessionCount = 0;

        for (Long sessionId : sessionIds) {
            try {
                SessionProcessingResult processingResult = sessionTransactionTemplate.execute(
                    status -> processSession(sessionId, requestId)
                );
                if (processingResult != null) {
                    expiredReservationCount += processingResult.expiredReservationCount();
                    completedSessionCount += processingResult.completedSessionCount();
                }
            } catch (RuntimeException exception) {
                failedSessionCount++;
                log.error(
                    "No-show and session completion processing failed. requestId={}, sessionId={}",
                    requestId,
                    sessionId,
                    exception
                );
            }
        }

        return new NoShowAndSessionCompletionResult(
            requestId,
            expiredReservationCount,
            completedSessionCount,
            failedSessionCount
        );
    }

    private SessionProcessingResult processSession(
        Long sessionId,
        UUID requestId
    ) {
        if (contentSessionService.findNoShowProcessingTargetForUpdate(sessionId).isEmpty()) {
            return new SessionProcessingResult(0, 0);
        }
        List<ReservationService.NoShowReservationAuditTarget> expiredReservations = reservationService
            .expireNoShowReservations(sessionId);
        expiredReservations.forEach(reservation -> recordNoShowAuditEvent(requestId, reservation));

        if (!contentSessionService.completeIfNoConfirmedReservation(sessionId)) {
            return new SessionProcessingResult(expiredReservations.size(), 0);
        }

        ContentSession completedSession = contentSessionService.findCompletedSessionForNoShowAudit(sessionId);
        recordSessionCompletionAuditEvent(requestId, completedSession);
        return new SessionProcessingResult(expiredReservations.size(), 1);
    }

    private void recordNoShowAuditEvent(
        UUID requestId,
        ReservationService.NoShowReservationAuditTarget reservation
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            reservation.region(),
            AuditEventTargetType.RESERVATION,
            reservation.reservationId(),
            "CONFIRMED",
            "EXPIRED",
            AuditEventResult.SUCCESS,
            NO_SHOW_REASON_CODE,
            null,
            reservation.expiredAt()
        ));
    }

    private void recordSessionCompletionAuditEvent(
        UUID requestId,
        ContentSession contentSession
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            contentSession.getRegion(),
            AuditEventTargetType.CONTENT_SESSION,
            contentSession.getSessionId(),
            "SCHEDULED",
            "COMPLETED",
            AuditEventResult.SUCCESS,
            null,
            null,
            contentSession.getCompletedAt()
        ));
    }

    private record SessionProcessingResult(
        int expiredReservationCount,
        int completedSessionCount
    ) {
    }
}
