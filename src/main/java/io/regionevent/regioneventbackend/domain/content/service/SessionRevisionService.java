package io.regionevent.regioneventbackend.domain.content.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.repository.SessionRevisionRepository;
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
}
