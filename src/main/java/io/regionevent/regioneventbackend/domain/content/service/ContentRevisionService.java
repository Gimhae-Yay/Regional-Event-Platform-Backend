package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
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
}
