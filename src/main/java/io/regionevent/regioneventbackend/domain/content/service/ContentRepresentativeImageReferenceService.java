package io.regionevent.regioneventbackend.domain.content.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;

@Service
public class ContentRepresentativeImageReferenceService {

    private final ContentRepository contentRepository;

    public ContentRepresentativeImageReferenceService(ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    @Transactional(readOnly = true)
    public boolean hasRepresentativeImageReference(Long imageObjectId) {
        return contentRepository.countByRepresentativeImageObjectImageObjectId(imageObjectId) > 0;
    }
}
