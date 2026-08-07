package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ContentSessionService {

    private static final List<ContentSessionStatus> END_TERMINAL_STATUSES = List.of(
        ContentSessionStatus.COMPLETED,
        ContentSessionStatus.CANCELLED,
        ContentSessionStatus.REJECTED
    );
    private static final List<ContentSessionStatus> OPERATOR_RESERVATION_LIST_STATUSES = List.of(
        ContentSessionStatus.SCHEDULED,
        ContentSessionStatus.COMPLETED,
        ContentSessionStatus.CANCELLED
    );
    private static final List<ContentStatus> PENDING_REVIEW_CONTENT_STATUSES = List.of(
        ContentStatus.APPROVED,
        ContentStatus.PUBLISHED
    );

    private final ContentSessionRepository contentSessionRepository;

    public ContentSessionService(ContentSessionRepository contentSessionRepository) {
        this.contentSessionRepository = contentSessionRepository;
    }

    public ContentSession findPublicSession(Long sessionId) {
        return contentSessionRepository.findBySessionIdAndContentStatus(
            sessionId,
            ContentStatus.PUBLISHED
        ).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public ContentSession findPendingReviewTarget(Long sessionId) {
        return contentSessionRepository.findPendingReviewTarget(
            sessionId,
            List.of(ContentStatus.APPROVED, ContentStatus.PUBLISHED)
        ).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ContentSession findRejectTargetForUpdate(Long sessionId) {
        return contentSessionRepository.findRejectTargetForUpdate(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public ContentSession findOperatorReservationListTarget(
        Long sessionId,
        Long contentId,
        Long regionId
    ) {
        return contentSessionRepository.findOperatorReservationListTarget(
            sessionId,
            contentId,
            regionId,
            OPERATOR_RESERVATION_LIST_STATUSES
        )
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<ContentSession> findCurrentSessionsByContentId(Long contentId) {
        return contentSessionRepository.findByContentContentIdOrderByStartsAtAscSessionIdAsc(contentId);
    }

    @Transactional(readOnly = true)
    public List<ContentSession> findPendingByContentId(Long contentId) {
        return contentSessionRepository.findByContentContentIdAndStatusOrderByStartsAtAsc(
            contentId,
            ContentSessionStatus.PENDING
        );
    }

    public List<ContentSession> findPendingReviewCandidatesByRegionId(Long regionId) {
        return contentSessionRepository.findPendingReviewCandidatesByRegionId(
            regionId,
            ContentSessionStatus.PENDING,
            PENDING_REVIEW_CONTENT_STATUSES
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long findContentIdBySessionId(Long sessionId) {
        return contentSessionRepository.findContentIdBySessionId(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long findPublicContentId(Long sessionId) {
        return contentSessionRepository.findPublicContentIdBySessionId(
            sessionId,
            ContentStatus.PUBLISHED
        ).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ContentSession findApprovalTargetForUpdate(Long sessionId) {
        return contentSessionRepository.findApprovalTargetBySessionIdForUpdate(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public void validatePendingSessionExists(Long contentId) {
        if (findPendingByContentId(contentId).isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    public List<ContentSession> findScheduledByContentId(Long contentId) {
        return contentSessionRepository.findByContentContentIdAndStatusOrderByStartsAtAsc(
            contentId,
            ContentSessionStatus.SCHEDULED
        );
    }

    public boolean hasNonTerminalSessionForEnd(Long contentId) {
        return contentSessionRepository.existsNonTerminalSessionForEnd(
            contentId,
            END_TERMINAL_STATUSES
        );
    }

    public List<ContentSessionStatus> getEndTerminalStatuses() {
        return END_TERMINAL_STATUSES;
    }

    public ContentSession findCancelTargetForUpdate(Long sessionId) {
        return contentSessionRepository.findCancelTargetForUpdate(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public boolean lockConfirmableReservationTarget(Long sessionId) {
        return contentSessionRepository.findConfirmableReservationTargetIdForUpdate(sessionId).isPresent();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForUpdate(Long sessionId) {
        findForUpdate(sessionId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ContentSession findForUpdate(Long sessionId) {
        return contentSessionRepository.findBySessionIdForUpdate(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ContentSession findRevisionTargetForUpdate(Long sessionId) {
        return contentSessionRepository.findRevisionTargetForUpdate(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public boolean isBeforeStartByDatabaseTime(Long sessionId) {
        return contentSessionRepository.countBeforeStartByDatabaseTime(sessionId) > 0;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ContentSession applyRevision(
        ContentSession contentSession,
        SessionRevision revision
    ) {
        if (contentSession.getStatus() != ContentSessionStatus.SCHEDULED
            || contentSession.getVersionNo() != revision.getBaseSessionVersion()) {
            throw new BusinessException(ErrorCode.SESSION_STATE_CONFLICT);
        }
        contentSession.applyRevision(
            revision.getStartsAt(),
            revision.getEndsAt(),
            revision.getCheckinOpenAt(),
            revision.getCheckinCloseAt(),
            revision.getCapacity()
        );
        return contentSessionRepository.saveAndFlush(contentSession);
    }

    public List<ContentSession> createPendingSessions(
        Content content,
        Region region,
        List<CreateContentSessionCommand> commands
    ) {
        List<ContentSession> sessions = commands.stream()
            .map(command -> new ContentSession(
                content,
                region,
                command.startsAt(),
                command.endsAt(),
                command.checkinOpenAt(),
                command.checkinCloseAt(),
                command.capacity()
            ))
            .toList();
        return contentSessionRepository.saveAllAndFlush(sessions);
    }

    public List<ContentSession> findApprovalTargetsForUpdate(Long contentId) {
        return contentSessionRepository.findApprovalTargetsForUpdate(contentId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockSuspendTargetsForUpdate(Long contentId) {
        contentSessionRepository.findSuspendTargetsForUpdate(contentId);
    }

    public List<ContentSession> approveAll(
        List<ContentSession> contentSessions,
        AppUser reviewer,
        Instant reviewedAt
    ) {
        if (contentSessions.isEmpty()
            || contentSessions.stream().anyMatch(
                contentSession -> contentSession.getStatus() != ContentSessionStatus.PENDING
            )) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }
        contentSessions.forEach(contentSession -> contentSession.approve(reviewer, reviewedAt));
        return contentSessionRepository.saveAllAndFlush(contentSessions);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ContentSession approve(
        ContentSession contentSession,
        AppUser reviewer,
        Instant reviewedAt
    ) {
        if (contentSession.getStatus() != ContentSessionStatus.PENDING) {
            throw new BusinessException(ErrorCode.SESSION_STATE_CONFLICT);
        }
        contentSession.approve(reviewer, reviewedAt);
        return contentSessionRepository.saveAndFlush(contentSession);
    }

    @Transactional(readOnly = true)
    public PublicSessionReservationInfo findPublicScheduledReservationInfo(Long sessionId) {
        return contentSessionRepository.findPublicScheduledReservationInfo(
            sessionId,
            ContentStatus.PUBLISHED,
            ContentSessionStatus.SCHEDULED
        ).map(PublicSessionReservationInfo::from)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public void reserveCapacity(Long sessionId, int quantity) {
        int updatedCount = contentSessionRepository.decreaseRemainingCapacityIfReservable(
            sessionId,
            quantity,
            ContentStatus.PUBLISHED,
            ContentSessionStatus.SCHEDULED
        );
        if (updatedCount == 0) {
            throw new BusinessException(ErrorCode.RESERVATION_HOLD_CONFLICT);
        }
    }

    public ContentSession cancel(
        ContentSession contentSession,
        AppUser operator,
        Instant cancelledAt,
        String cancellationReason
    ) {
        if (contentSession.getStatus() != ContentSessionStatus.SCHEDULED) {
            throw new BusinessException(ErrorCode.SESSION_NOT_CANCELLABLE);
        }
        contentSession.cancel(operator, cancelledAt, cancellationReason);
        return contentSessionRepository.saveAndFlush(contentSession);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ContentSession reject(
        ContentSession contentSession,
        AppUser reviewer,
        Instant reviewedAt,
        String rejectReason
    ) {
        if (contentSession.getStatus() != ContentSessionStatus.PENDING
            || !PENDING_REVIEW_CONTENT_STATUSES.contains(contentSession.getContent().getStatus())) {
            throw new BusinessException(ErrorCode.SESSION_STATE_CONFLICT);
        }
        contentSession.reject(reviewer, reviewedAt, rejectReason);
        return contentSessionRepository.saveAndFlush(contentSession);
    }

    public ContentSession releaseCapacity(ContentSession contentSession, int quantity) {
        if (quantity == 0) {
            return contentSession;
        }
        contentSession.releaseCapacity(quantity);
        return contentSessionRepository.saveAndFlush(contentSession);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreCapacity(Long sessionId, int quantity) {
        int updatedCount = contentSessionRepository.increaseRemainingCapacityIfWithinCapacity(sessionId, quantity);
        if (updatedCount == 0) {
            throw new IllegalStateException("failed to restore content session capacity");
        }
    }

    @Transactional(readOnly = true)
    public List<Long> findNoShowProcessingTargetSessionIds() {
        return contentSessionRepository.findNoShowProcessingTargetSessionIds(ContentSessionStatus.SCHEDULED);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ContentSession> findNoShowProcessingTargetForUpdate(Long sessionId) {
        return contentSessionRepository.findNoShowProcessingTargetForUpdate(
            sessionId,
            ContentSessionStatus.SCHEDULED
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean completeIfNoConfirmedReservation(Long sessionId) {
        return contentSessionRepository.completeIfNoConfirmedReservation(sessionId) == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public ContentSession findCompletedSessionForNoShowAudit(Long sessionId) {
        return contentSessionRepository.findCompletedSessionForNoShowAudit(
            sessionId,
            ContentSessionStatus.COMPLETED
        ).orElseThrow(() -> new IllegalStateException("completed content session does not exist"));
    }

    public record CreateContentSessionCommand(
        Instant startsAt,
        Instant endsAt,
        Instant checkinOpenAt,
        Instant checkinCloseAt,
        int capacity
    ) {
    }
}
