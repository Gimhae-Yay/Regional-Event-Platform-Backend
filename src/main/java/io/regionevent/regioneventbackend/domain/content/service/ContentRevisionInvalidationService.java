package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionInvalidationReason;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

@Service
public class ContentRevisionInvalidationService {

    private final ContentRevisionRepository contentRevisionRepository;

    public ContentRevisionInvalidationService(ContentRevisionRepository contentRevisionRepository) {
        this.contentRevisionRepository = contentRevisionRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ContentRevision> invalidateActiveRevisionForContent(
        Long contentId,
        AppUser invalidator,
        Instant invalidatedAt,
        ContentRevisionInvalidationReason reason
    ) {
        return contentRevisionRepository.findByContentContentIdAndStatusForUpdate(
            contentId,
            ContentRevisionStatus.EDIT_REQUESTED
        ).map(revision -> {
            revision.invalidate(invalidator, invalidatedAt, reason);
            return revision;
        });
    }
}
