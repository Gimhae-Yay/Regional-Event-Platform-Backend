package io.regionevent.regioneventbackend.domain.content.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ContentRevisionService {

    private final ContentRevisionRepository contentRevisionRepository;

    public ContentRevisionService(ContentRevisionRepository contentRevisionRepository) {
        this.contentRevisionRepository = contentRevisionRepository;
    }

    @Transactional(readOnly = true)
    public ContentRevisionReviewCandidate findReviewCandidateById(Long contentRevisionId) {
        return contentRevisionRepository
            .findByContentRevisionIdAndStatusAndContentDeletedAtIsNull(
                contentRevisionId,
                ContentRevisionStatus.EDIT_REQUESTED
            )
            .map(ContentRevisionReviewCandidate::from)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<ContentRevisionReviewCandidate> findReviewCandidatesByRegionId(Long regionId) {
        return contentRevisionRepository
            .findByContentRegionRegionIdAndStatusAndContentDeletedAtIsNullOrderBySubmittedAtAscContentRevisionIdAsc(
                regionId,
                ContentRevisionStatus.EDIT_REQUESTED
            )
            .stream()
            .map(ContentRevisionReviewCandidate::from)
            .toList();
    }
}
