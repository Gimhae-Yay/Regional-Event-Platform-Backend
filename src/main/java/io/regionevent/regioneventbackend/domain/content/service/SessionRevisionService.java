package io.regionevent.regioneventbackend.domain.content.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.repository.SessionRevisionRepository;

@Service
public class SessionRevisionService {

    private final SessionRevisionRepository sessionRevisionRepository;

    public SessionRevisionService(SessionRevisionRepository sessionRevisionRepository) {
        this.sessionRevisionRepository = sessionRevisionRepository;
    }

    @Transactional(readOnly = true)
    public List<SessionRevision> findPendingByRegionId(Long regionId) {
        return sessionRevisionRepository.findByRegionIdAndStatusForReview(
            regionId,
            SessionRevisionStatus.PENDING
        );
    }
}
