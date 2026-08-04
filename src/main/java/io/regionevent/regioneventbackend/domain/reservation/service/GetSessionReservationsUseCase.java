package io.regionevent.regioneventbackend.domain.reservation.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;

@Service
public class GetSessionReservationsUseCase {

    private final ContentService contentService;
    private final ContentSessionService contentSessionService;
    private final OperatorAuthorizationService operatorAuthorizationService;
    private final SessionReservationReadService sessionReservationReadService;

    public GetSessionReservationsUseCase(
        ContentService contentService,
        ContentSessionService contentSessionService,
        OperatorAuthorizationService operatorAuthorizationService,
        SessionReservationReadService sessionReservationReadService
    ) {
        this.contentService = contentService;
        this.contentSessionService = contentSessionService;
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.sessionReservationReadService = sessionReservationReadService;
    }

    @Transactional(readOnly = true)
    public SessionReservationListResult find(
        Long operatorUserId,
        Long contentId,
        Long sessionId
    ) {
        Content content = contentService.findOperatorReservationListTarget(contentId);
        operatorAuthorizationService.authorizeOwnedContent(
            operatorUserId,
            content.getOperator(),
            content.getRegion()
        );
        ContentSession session = contentSessionService.findOperatorReservationListTarget(
            sessionId,
            content.getContentId(),
            content.getRegion().getRegionId()
        );
        List<SessionReservationReadResult> reservations = sessionReservationReadService.findBySessionId(
            session.getSessionId()
        );

        return new SessionReservationListResult(
            content.getContentId(),
            new SessionReservationListResult.SessionInfo(
                session.getSessionId(),
                session.getStatus(),
                session.getStartsAt(),
                session.getEndsAt(),
                session.getCheckinOpenAt(),
                session.getCheckinCloseAt()
            ),
            reservations
        );
    }
}
