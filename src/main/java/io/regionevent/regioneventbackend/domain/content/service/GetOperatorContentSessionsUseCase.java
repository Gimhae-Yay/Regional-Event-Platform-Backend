package io.regionevent.regioneventbackend.domain.content.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetOperatorContentSessionsUseCase {

    private final ContentService contentService;
    private final ContentSessionService contentSessionService;
    private final SessionRevisionService sessionRevisionService;
    private final OperatorAuthorizationService operatorAuthorizationService;

    public GetOperatorContentSessionsUseCase(
        ContentService contentService,
        ContentSessionService contentSessionService,
        SessionRevisionService sessionRevisionService,
        OperatorAuthorizationService operatorAuthorizationService
    ) {
        this.contentService = contentService;
        this.contentSessionService = contentSessionService;
        this.sessionRevisionService = sessionRevisionService;
        this.operatorAuthorizationService = operatorAuthorizationService;
    }

    @Transactional(readOnly = true)
    public OperatorContentSessionListResult get(Long authenticatedUserId, Long contentId) {
        Content content = contentService.findOperatorReservationListTarget(contentId);
        operatorAuthorizationService.authorizeOwnedContent(
            authenticatedUserId,
            content.getOperator(),
            content.getRegion()
        );

        List<ContentSession> contentSessions = contentSessionService.findCurrentSessionsByContentId(contentId);
        Map<Long, SessionRevision> pendingRevisions = toPendingRevisionMap(
            content,
            contentSessions,
            sessionRevisionService.findPendingByTargetContentId(contentId)
        );
        List<OperatorContentSessionListResult.Session> sessions = contentSessions.stream()
            .map(contentSession -> toSession(content, contentSession, pendingRevisions.get(contentSession.getSessionId())))
            .toList();
        return new OperatorContentSessionListResult(contentId, sessions);
    }

    private Map<Long, SessionRevision> toPendingRevisionMap(
        Content content,
        List<ContentSession> contentSessions,
        List<SessionRevision> pendingRevisions
    ) {
        Map<Long, ContentSession> sessionsById = new HashMap<>();
        for (ContentSession contentSession : contentSessions) {
            validateSessionRelations(content, contentSession);
            if (sessionsById.putIfAbsent(contentSession.getSessionId(), contentSession) != null) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }

        Map<Long, SessionRevision> revisionsBySessionId = new HashMap<>();
        for (SessionRevision pendingRevision : pendingRevisions) {
            validateRevisionRelations(content, sessionsById, pendingRevision);
            Long targetSessionId = pendingRevision.getTargetSession().getSessionId();
            if (revisionsBySessionId.putIfAbsent(targetSessionId, pendingRevision) != null) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }
        return revisionsBySessionId;
    }

    private void validateSessionRelations(Content content, ContentSession contentSession) {
        if (contentSession == null
            || contentSession.getSessionId() == null
            || contentSession.getContent() == null
            || contentSession.getContent().getContentId() == null
            || contentSession.getRegion() == null
            || contentSession.getRegion().getRegionId() == null
            || content.getContentId() == null
            || content.getRegion() == null
            || content.getRegion().getRegionId() == null
            || !content.getContentId().equals(contentSession.getContent().getContentId())
            || !content.getRegion().getRegionId().equals(contentSession.getRegion().getRegionId())) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void validateRevisionRelations(
        Content content,
        Map<Long, ContentSession> sessionsById,
        SessionRevision revision
    ) {
        if (revision == null
            || revision.getSessionRevisionId() == null
            || revision.getStatus() != SessionRevisionStatus.PENDING
            || revision.getContent() == null
            || revision.getContent().getContentId() == null
            || revision.getRegion() == null
            || revision.getRegion().getRegionId() == null
            || revision.getTargetSession() == null
            || revision.getTargetSession().getSessionId() == null
            || !content.getContentId().equals(revision.getContent().getContentId())
            || !content.getRegion().getRegionId().equals(revision.getRegion().getRegionId())
            || !sessionsById.containsKey(revision.getTargetSession().getSessionId())) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        validateSessionRelations(content, revision.getTargetSession());
    }

    private OperatorContentSessionListResult.Session toSession(
        Content content,
        ContentSession contentSession,
        SessionRevision pendingRevision
    ) {
        validateSessionRelations(content, contentSession);
        return new OperatorContentSessionListResult.Session(
            contentSession.getSessionId(),
            contentSession.getStatus(),
            contentSession.getVersionNo(),
            contentSession.getStartsAt(),
            contentSession.getEndsAt(),
            contentSession.getCheckinOpenAt(),
            contentSession.getCheckinCloseAt(),
            contentSession.getCapacity(),
            contentSession.getRemainingCapacity(),
            contentSession.getRejectReason(),
            contentSession.getCancelledAt(),
            contentSession.getCancellationReason(),
            contentSession.getCompletedAt(),
            contentSession.getCreatedAt(),
            toPendingChangeRequest(pendingRevision)
        );
    }

    private OperatorContentSessionListResult.PendingChangeRequest toPendingChangeRequest(
        SessionRevision revision
    ) {
        if (revision == null) {
            return null;
        }
        return new OperatorContentSessionListResult.PendingChangeRequest(
            revision.getSessionRevisionId(),
            revision.getStatus(),
            revision.getBaseSessionVersion(),
            new OperatorContentSessionListResult.Candidate(
                revision.getStartsAt(),
                revision.getEndsAt(),
                revision.getCheckinOpenAt(),
                revision.getCheckinCloseAt(),
                revision.getCapacity()
            ),
            revision.getSubmittedAt()
        );
    }
}
