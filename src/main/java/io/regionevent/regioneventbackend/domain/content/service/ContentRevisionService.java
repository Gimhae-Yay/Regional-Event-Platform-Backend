package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ContentRevisionService {

    private final ContentRevisionRepository contentRevisionRepository;

    public ContentRevisionService(ContentRevisionRepository contentRevisionRepository) {
        this.contentRevisionRepository = contentRevisionRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ContentRevision findReviewTargetForUpdate(Long revisionId) {
        return contentRevisionRepository.findReviewTargetByIdForUpdate(revisionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ContentRevision reject(
        ContentRevision revision,
        AppUser reviewer,
        Instant reviewedAt,
        String reason
    ) {
        validateRejectableOriginal(revision);
        revision.reject(reviewer, reviewedAt, reason);
        contentRevisionRepository.flush();
        return revision;
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

    private void validateRejectableOriginal(ContentRevision revision) {
        Content content = revision.getContent();
        if (content.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        boolean publishedRevision = content.getStatus() == ContentStatus.PUBLISHED
            && revision.getPublishAt() == null;
        boolean prePublicationRevision = content.getStatus() == ContentStatus.PENDING
            && revision.getPublishAt() != null;
        if (!publishedRevision && !prePublicationRevision) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }
    }
}
