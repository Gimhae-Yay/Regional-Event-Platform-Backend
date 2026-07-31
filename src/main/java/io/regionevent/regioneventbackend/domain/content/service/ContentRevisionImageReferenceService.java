package io.regionevent.regioneventbackend.domain.content.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;

@Service
public class ContentRevisionImageReferenceService {

    private final ContentRevisionRepository contentRevisionRepository;

    public ContentRevisionImageReferenceService(ContentRevisionRepository contentRevisionRepository) {
        this.contentRevisionRepository = contentRevisionRepository;
    }

    @Transactional(readOnly = true)
    public boolean hasCandidateImageReference(Long imageObjectId) {
        return contentRevisionRepository.countByCandidateImageObjectImageObjectId(imageObjectId) > 0;
    }
}
