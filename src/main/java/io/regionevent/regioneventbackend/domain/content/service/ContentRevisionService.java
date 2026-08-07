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
import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ContentRevisionService {

    private final ContentRevisionRepository contentRevisionRepository;

    public ContentRevisionService(ContentRevisionRepository contentRevisionRepository) {
        this.contentRevisionRepository = contentRevisionRepository;
    }

    public ContentRevision createEditRequestedRevision(
        Content content,
        AppUser editor,
        CreateContentRevisionCommand command,
        ImageObject candidateImageObject,
        Instant candidateImageAssignedAt,
        Instant submittedAt
    ) {
        if (contentRevisionRepository.existsByContentContentIdAndStatus(
            content.getContentId(),
            ContentRevisionStatus.EDIT_REQUESTED
        )) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }

        ContentRevision contentRevision = new ContentRevision(
            content,
            contentRevisionRepository.findMaxRevisionNoByContentId(content.getContentId()) + 1,
            content.getVersionNo(),
            editor,
            ContentRevisionStatus.EDIT_REQUESTED,
            command.title(),
            command.description(),
            command.locationText(),
            command.operatingHoursText(),
            command.contactText(),
            command.precautions(),
            command.ageRequirement(),
            command.materials(),
            command.cancellationPolicyText(),
            command.publishAt(),
            submittedAt,
            null,
            null,
            null,
            null,
            null,
            null
        );
        contentRevision.assignCandidateImage(candidateImageObject, candidateImageAssignedAt);
        return contentRevisionRepository.saveAndFlush(contentRevision);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long findContentIdByRevisionId(Long revisionId) {
        return contentRevisionRepository.findContentIdByContentRevisionId(revisionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ContentRevision findReviewTargetForUpdate(Long revisionId) {
        return contentRevisionRepository.findReviewTargetByIdForUpdate(revisionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ContentRevision findRejectedRevisionForUpdate(Long revisionId) {
        ContentRevision contentRevision = contentRevisionRepository.findByContentRevisionId(revisionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (contentRevision.getStatus() != ContentRevisionStatus.EDIT_REJECTED) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }
        return contentRevision;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ContentRevision updateRejectedRevision(
        ContentRevision contentRevision,
        UpdateContentRevisionCommand command
    ) {
        contentRevision.replaceRejectedCandidateFields(
            command.title(),
            command.description(),
            command.locationText(),
            command.operatingHoursText(),
            command.contactText(),
            command.precautions(),
            command.ageRequirement(),
            command.materials(),
            command.cancellationPolicyText(),
            command.publishAt()
        );
        if (command.candidateImageObject() != null) {
            contentRevision.assignCandidateImage(
                command.candidateImageObject(),
                command.candidateImageAssignedAt()
            );
        }
        contentRevisionRepository.flush();
        return contentRevision;
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

    @Transactional(propagation = Propagation.MANDATORY)
    public ContentRevision withdraw(
        ContentRevision revision,
        AppUser withdrawer,
        Instant withdrawnAt,
        String reason
    ) {
        validateRejectableOriginal(revision);
        revision.withdraw(withdrawer, withdrawnAt, reason);
        contentRevisionRepository.flush();
        return revision;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ContentRevision approve(
        ContentRevision revision,
        AppUser reviewer,
        Instant reviewedAt,
        boolean isPrePublicationRevisionByHistory
    ) {
        Content content = revision.getContent();
        validateApprovable(revision, content, isPrePublicationRevisionByHistory);
        applyCandidateFields(revision, content, reviewedAt);
        if (content.getStatus() == ContentStatus.PENDING) {
            content.approve();
        }
        revision.approve(reviewer, reviewedAt);
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

    private void validateApprovable(
        ContentRevision revision,
        Content content,
        boolean isPrePublicationRevisionByHistory
    ) {
        if (content.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (revision.getStatus() != ContentRevisionStatus.EDIT_REQUESTED
            || revision.getBaseContentVersion() != content.getVersionNo()) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }

        boolean publishedRevision = content.getStatus() == ContentStatus.PUBLISHED
            && revision.getPublishAt() == null;
        boolean prePublicationRevision = content.getStatus() == ContentStatus.PENDING
            && revision.getPublishAt() != null
            && isPrePublicationRevisionByHistory;
        if (!publishedRevision && !prePublicationRevision) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }
        validateCandidateImage(revision, content);
    }

    private void validateCandidateImage(ContentRevision revision, Content content) {
        ImageObject candidateImage = revision.getCandidateImageObject();
        if (candidateImage == null
            || revision.getCandidateImageAssignedAt() == null
            || candidateImage.getLifecycleStatus() != ImageLifecycleStatus.ACTIVE
            || candidateImage.getLinkedAt() == null
            || !candidateImage.isScopedTo(content.getRegion().getRegionId())) {
            throw new IllegalStateException("content revision candidate image must be active and linked");
        }
    }

    private void applyCandidateFields(
        ContentRevision revision,
        Content content,
        Instant appliedAt
    ) {
        Instant publishAt = revision.getPublishAt() == null
            ? content.getPublishAt()
            : revision.getPublishAt();
        content.replaceEditableFields(
            revision.getTitle(),
            revision.getDescription(),
            revision.getLocationText(),
            revision.getOperatingHoursText(),
            revision.getContactText(),
            revision.getPrecautions(),
            revision.getAgeRequirement(),
            revision.getMaterials(),
            revision.getCancellationPolicyText(),
            publishAt
        );
        content.assignRepresentativeImage(revision.getCandidateImageObject(), appliedAt);
    }

    public record CreateContentRevisionCommand(
        String title,
        String description,
        String locationText,
        String operatingHoursText,
        String contactText,
        String precautions,
        String ageRequirement,
        String materials,
        String cancellationPolicyText,
        Instant publishAt
    ) {
    }

    public record UpdateContentRevisionCommand(
        String title,
        String description,
        String locationText,
        String operatingHoursText,
        String contactText,
        String precautions,
        String ageRequirement,
        String materials,
        String cancellationPolicyText,
        Instant publishAt,
        ImageObject candidateImageObject,
        Instant candidateImageAssignedAt
    ) {
    }
}
