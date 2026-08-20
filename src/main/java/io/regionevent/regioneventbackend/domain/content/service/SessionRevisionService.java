package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.repository.SessionRevisionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class SessionRevisionService {

    private static final String PENDING_TARGET_SESSION_UNIQUE_CONSTRAINT =
        "uk_session_revision_pending_target_session";

    private final SessionRevisionRepository sessionRevisionRepository;

    public SessionRevisionService(SessionRevisionRepository sessionRevisionRepository) {
        this.sessionRevisionRepository = sessionRevisionRepository;
    }

    @Transactional(readOnly = true)
    public SessionRevision findPendingReviewDetailById(Long revisionId) {
        return sessionRevisionRepository.findPendingReviewDetailById(
            revisionId,
            SessionRevisionStatus.PENDING
        ).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SessionRevision createPending(
        ContentSession targetSession,
        AppUser requestedBy,
        CreateSessionRevisionCommand command,
        Instant submittedAt
    ) {
        try {
            return sessionRevisionRepository.saveAndFlush(new SessionRevision(
                targetSession.getContent(),
                targetSession.getRegion(),
                targetSession,
                targetSession.getVersionNo(),
                command.startsAt(),
                command.endsAt(),
                command.checkinOpenAt(),
                command.checkinCloseAt(),
                command.capacity(),
                SessionRevisionStatus.PENDING,
                requestedBy,
                submittedAt,
                null,
                null,
                null
            ));
        } catch (DataIntegrityViolationException exception) {
            if (isPendingTargetSessionUniqueConstraintViolation(exception)) {
                throw new BusinessException(ErrorCode.SESSION_STATE_CONFLICT, exception);
            }
            throw exception;
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SessionRevision findReviewTargetForUpdate(Long revisionId) {
        return sessionRevisionRepository.findReviewTargetByIdForUpdate(revisionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void rejectPending(
        Long revisionId,
        AppUser reviewer,
        Instant reviewedAt,
        String reason
    ) {
        if (sessionRevisionRepository.rejectPendingById(revisionId, reviewer, reviewedAt, reason) != 1) {
            throw new BusinessException(ErrorCode.SESSION_STATE_CONFLICT);
        }
    }

    @Transactional(readOnly = true)
    public List<SessionRevision> findPendingByRegionId(Long regionId) {
        return sessionRevisionRepository.findByRegionIdAndStatusForReview(
            regionId,
            SessionRevisionStatus.PENDING
        );
    }

    @Transactional(readOnly = true)
    public List<SessionRevision> findPendingByTargetContentId(Long contentId) {
        return sessionRevisionRepository.findPendingByTargetContentId(
            contentId,
            SessionRevisionStatus.PENDING
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long findContentIdByRevisionId(Long revisionId) {
        return sessionRevisionRepository.findContentIdBySessionRevisionId(revisionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SessionRevision findApprovalTargetForUpdate(Long revisionId) {
        return sessionRevisionRepository.findApprovalTargetForUpdate(revisionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SessionRevision approve(
        SessionRevision revision,
        AppUser reviewer,
        Instant reviewedAt
    ) {
        if (revision.getStatus() != SessionRevisionStatus.PENDING) {
            throw new BusinessException(ErrorCode.SESSION_STATE_CONFLICT);
        }
        revision.approve(reviewer, reviewedAt);
        sessionRevisionRepository.flush();
        return revision;
    }

    private static boolean isPendingTargetSessionUniqueConstraintViolation(
        DataIntegrityViolationException exception
    ) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException
                && PENDING_TARGET_SESSION_UNIQUE_CONSTRAINT.equalsIgnoreCase(
                    constraintViolationException.getConstraintName()
                )) {
                return true;
            }
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(
                PENDING_TARGET_SESSION_UNIQUE_CONSTRAINT
            )) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    public record CreateSessionRevisionCommand(
        Instant startsAt,
        Instant endsAt,
        Instant checkinOpenAt,
        Instant checkinCloseAt,
        int capacity
    ) {
    }
}
