package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.MyContentProjection;
import io.regionevent.regioneventbackend.domain.content.repository.PublicContentProjection;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ContentService {

    private final ContentRepository contentRepository;

    public ContentService(ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    public Content createPendingContent(
        Region region,
        AppUser operator,
        ImageObject representativeImageObject,
        CreateContentCommand command,
        Instant representativeImageAssignedAt
    ) {
        Content content = new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PENDING,
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
        content.assignRepresentativeImage(representativeImageObject, representativeImageAssignedAt);
        return contentRepository.saveAndFlush(content);
    }

    public Content findOwnedContentForRevisionCreation(
        Long contentId,
        Long operatorUserId,
        Long regionId
    ) {
        validateRequiredId(contentId);
        validateRequiredId(operatorUserId);
        validateRequiredId(regionId);

        Content content = contentRepository.findByContentIdAndDeletedAtIsNull(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!content.isOwnedBy(operatorUserId) || !content.isScopedTo(regionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return content;
    }

    public Content markPrePublicationRevisionPending(Content content) {
        if (content.getStatus() != ContentStatus.APPROVED) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }
        content.requestPrePublicationRevision();
        return contentRepository.saveAndFlush(content);
    }

    public Content findRejectedOwnedContentForUpdate(
        Long contentId,
        Long operatorUserId,
        Long regionId
    ) {
        validateRequiredId(contentId);
        validateRequiredId(operatorUserId);
        validateRequiredId(regionId);

        Content content = contentRepository.findByContentIdAndDeletedAtIsNull(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!content.isOwnedBy(operatorUserId) || !content.isScopedTo(regionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (content.getStatus() != ContentStatus.REJECTED) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }
        return content;
    }

    public Content updateRejectedContent(
        Content content,
        UpdateContentCommand command,
        ImageObject replacementImageObject,
        Instant representativeImageAssignedAt
    ) {
        if (content.getStatus() != ContentStatus.REJECTED) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }
        content.replaceEditableFields(
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
        if (replacementImageObject != null) {
            content.assignRepresentativeImage(replacementImageObject, representativeImageAssignedAt);
        }
        return contentRepository.saveAndFlush(content);
    }

    public boolean existsPublishedAndNotDeletedById(Long contentId) {
        return contentRepository.existsByContentIdAndStatusAndDeletedAtIsNull(
            contentId,
            ContentStatus.PUBLISHED
        );
    }

    public boolean hasOwnedContent(Long userId) {
        return contentRepository.existsByOperatorUserId(userId);
    }

    public Content findPublicContent(Long contentId) {
        validateRequiredId(contentId);
        return contentRepository.findByContentIdAndStatusAndDeletedAtIsNull(
            contentId,
            ContentStatus.PUBLISHED
        ).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public Content findMyContentDetail(Long contentId) {
        validateRequiredId(contentId);
        return contentRepository.findDetailByContentIdAndDeletedAtIsNull(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Content findOperatorReservationListTarget(Long contentId) {
        validateRequiredId(contentId);
        return contentRepository.findOperatorReservationListTarget(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public List<PublicContentProjection> findPublicContents(
        Long regionId,
        ContentType contentType,
        Boolean reservationAvailable
    ) {
        return contentRepository.findPublicContents(
            regionId,
            contentType,
            reservationAvailable,
            ContentStatus.PUBLISHED,
            ContentSessionStatus.SCHEDULED
        );
    }

    public List<MyContentProjection> findMyContents(Long operatorUserId, Long regionId) {
        validateRequiredId(operatorUserId);
        validateRequiredId(regionId);
        return contentRepository.findMyContents(operatorUserId, regionId);
    }

    @Transactional(readOnly = true)
    public List<Content> findPendingReviewContentsByRegionId(Long regionId) {
        validateRequiredId(regionId);
        return contentRepository.findByRegionRegionIdAndStatusAndDeletedAtIsNullOrderByContentIdAsc(
            regionId,
            ContentStatus.PENDING
        );
    }

    public Content findApprovalTargetForUpdate(Long contentId) {
        return contentRepository.findApprovalTargetForUpdate(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public Content findEndTargetForUpdate(Long contentId) {
        return contentRepository.findEndTargetForUpdate(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<Long> findAutoEndCandidateIds(List<ContentSessionStatus> terminalStatuses) {
        return contentRepository.findAutoEndCandidateIds(ContentStatus.PUBLISHED, terminalStatuses);
    }

    public Instant findDatabaseCurrentInstant() {
        return contentRepository.findCurrentTimestamp();
    }

    public Content findDeletionTargetForUpdate(Long contentId) {
        validateRequiredId(contentId);
        return contentRepository.findDeletionTargetForUpdate(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public Content findSuspendTargetForUpdate(Long contentId) {
        return contentRepository.findSuspendTargetForUpdate(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public boolean lockPublishedReservationTarget(Long contentId) {
        return contentRepository.findPublishedReservationTargetIdForUpdate(contentId).isPresent();
    }

    public Content approve(Content content) {
        content.approve();
        return contentRepository.saveAndFlush(content);
    }

    public Content reject(Content content, Instant rejectedAt) {
        int updatedCount = contentRepository.rejectPendingByContentId(
            content.getContentId(),
            rejectedAt
        );
        if (updatedCount != 1) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }
        content.reject();
        return content;
    }

    public void validateSubmitRequirements(Content content) {
        if (content.getRepresentativeImageObject() == null
            || isBlank(content.getTitle())
            || isBlank(content.getDescription())
            || isBlank(content.getLocationText())
            || isBlank(content.getOperatingHoursText())
            || isBlank(content.getContactText())
            || isBlank(content.getPrecautions())
            || isBlank(content.getAgeRequirement())
            || isBlank(content.getMaterials())
            || isBlank(content.getCancellationPolicyText())
            || content.getPublishAt() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    public Content submitForReview(Content content, Instant submittedAt) {
        int updatedCount = contentRepository.submitRejectedByContentId(
            content.getContentId(),
            submittedAt
        );
        if (updatedCount != 1) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }
        content.submitForReview();
        return content;
    }

    public Content end(Content content, Instant endedAt) {
        int updatedCount = contentRepository.endPublishedByContentId(
            content.getContentId(),
            endedAt
        );
        if (updatedCount != 1) {
            throw new BusinessException(ErrorCode.CONTENT_END_CONFLICT);
        }
        content.end();
        return content;
    }

    public ImageObject softDelete(Content content, Instant deletedAt) {
        if ((content.getStatus() != ContentStatus.PENDING
            && content.getStatus() != ContentStatus.APPROVED)
            || content.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.CONTENT_DELETE_CONFLICT);
        }
        content.softDelete(deletedAt);
        ImageObject detachedImageObject = content.detachRepresentativeImage();
        if (detachedImageObject == null) {
            throw new IllegalStateException("content representative image must exist before deletion");
        }
        contentRepository.saveAndFlush(content);
        return detachedImageObject;
    }

    public Content suspend(Content content, Instant suspendedAt) {
        int updatedCount = contentRepository.suspendPublishedByContentId(
            content.getContentId(),
            suspendedAt
        );
        if (updatedCount != 1) {
            throw new BusinessException(ErrorCode.CONTENT_SUSPEND_CONFLICT);
        }
        content.suspend();
        return content;
    }

    private void validateRequiredId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record CreateContentCommand(
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

    public record UpdateContentCommand(
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
}
