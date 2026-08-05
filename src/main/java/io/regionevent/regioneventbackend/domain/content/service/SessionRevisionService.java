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

    @Transactional(readOnly = true)
    public List<SessionRevision> findPendingByRegionId(Long regionId) {
        return sessionRevisionRepository.findByRegionIdAndStatusForReview(
            regionId,
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
}
