package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.repository.ImageObjectRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ContentService {

    private final ContentRepository contentRepository;
    private final ImageObjectRepository imageObjectRepository;

    public ContentService(
        ContentRepository contentRepository,
        ImageObjectRepository imageObjectRepository
    ) {
        this.contentRepository = contentRepository;
        this.imageObjectRepository = imageObjectRepository;
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
        ImageObject previousRepresentativeImageObject = content.getRepresentativeImageObject();
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
        Content updatedContent = contentRepository.saveAndFlush(content);
        if (replacementImageObject != null) {
            markPreviousRepresentativeImageDeletePending(
                previousRepresentativeImageObject,
                replacementImageObject
            );
        }
        return updatedContent;
    }

    private void markPreviousRepresentativeImageDeletePending(
        ImageObject previousRepresentativeImageObject,
        ImageObject replacementImageObject
    ) {
        if (previousRepresentativeImageObject == null) {
            return;
        }
        if (previousRepresentativeImageObject.getImageObjectId()
            .equals(replacementImageObject.getImageObjectId())) {
            return;
        }
        int updatedCount = imageObjectRepository.markActiveObjectDeletePendingWithoutDirectReferences(
            previousRepresentativeImageObject.getImageObjectId(),
            ImageLifecycleStatus.ACTIVE,
            ImageLifecycleStatus.DELETE_PENDING
        );
        if (updatedCount > 0) {
            previousRepresentativeImageObject.markDeletePending();
        }
    }

    private void validateRequiredId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
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
