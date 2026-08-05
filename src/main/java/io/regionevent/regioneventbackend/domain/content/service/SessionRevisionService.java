package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.repository.SessionRevisionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class SessionRevisionService {

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
}
